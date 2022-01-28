/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package kotlinx.coroutines.channels

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow.*
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.Channel.Factory.CHANNEL_DEFAULT_CAPACITY
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.internal.*
import kotlinx.coroutines.selects.*
import kotlin.native.concurrent.*

/**
 * Broadcast channel is a non-blocking primitive for communication between the sender and multiple receivers
 * that subscribe for the elements using [openSubscription] function and unsubscribe using [ReceiveChannel.cancel]
 * function.
 *
 * See `BroadcastChannel()` factory function for the description of available
 * broadcast channel implementations.
 *
 * **Note: This API is obsolete since 1.5.0.** It will be deprecated with warning in 1.6.0
 * and with error in 1.7.0. It is replaced with [SharedFlow][kotlinx.coroutines.flow.SharedFlow].
 */
@ObsoleteCoroutinesApi
public interface BroadcastChannel<E> : SendChannel<E> {
    /**
     * Subscribes to this [BroadcastChannel] and returns a channel to receive elements from it.
     * The resulting channel shall be [cancelled][ReceiveChannel.cancel] to unsubscribe from this
     * broadcast channel.
     */
    public fun openSubscription(): ReceiveChannel<E>

    /**
     * Cancels reception of remaining elements from this channel with an optional cause.
     * This function closes the channel with
     * the specified cause (unless it was already closed), removes all buffered sent elements from it,
     * and [cancels][ReceiveChannel.cancel] all open subscriptions.
     * A cause can be used to specify an error message or to provide other details on
     * a cancellation reason for debugging purposes.
     */
    public fun cancel(cause: CancellationException? = null)

    /**
     * @suppress This method has bad semantics when cause is not a [CancellationException]. Use [cancel].
     */
    @Deprecated(level = DeprecationLevel.HIDDEN, message = "Binary compatibility only")
    public fun cancel(cause: Throwable? = null): Boolean
}

/**
 * Creates a broadcast channel with the specified buffer capacity.
 *
 * The resulting channel type depends on the specified [capacity] parameter:
 *
 * * when `capacity` positive, but less than [UNLIMITED] -- creates `ArrayBroadcastChannel` with a buffer of given capacity.
 *   **Note:** this channel looses all items that are send to it until the first subscriber appears;
 * * when `capacity` is [CONFLATED] -- creates [ConflatedBroadcastChannel] that conflates back-to-back sends;
 * * when `capacity` is [BUFFERED] -- creates `ArrayBroadcastChannel` with a default capacity.
 * * otherwise -- throws [IllegalArgumentException].
 *
 * **Note: This API is obsolete since 1.5.0.** It will be deprecated with warning in 1.6.0
 * and with error in 1.7.0. It is replaced with [StateFlow][kotlinx.coroutines.flow.StateFlow]
 * and [SharedFlow][kotlinx.coroutines.flow.SharedFlow].
 */
@ObsoleteCoroutinesApi
public fun <E> BroadcastChannel(capacity: Int): BroadcastChannel<E> =
    when (capacity) {
        0 -> throw IllegalArgumentException("Unsupported 0 capacity for BroadcastChannel")
        UNLIMITED -> throw IllegalArgumentException("Unsupported UNLIMITED capacity for BroadcastChannel")
        CONFLATED -> ConflatedBroadcastChannel()
        BUFFERED -> BroadcastChannelImpl(CHANNEL_DEFAULT_CAPACITY)
        else -> BroadcastChannelImpl(capacity)
    }

/**
 * Broadcasts the most recently sent element (aka [value]) to all [openSubscription] subscribers.
 *
 * Back-to-send sent elements are _conflated_ -- only the most recently sent value is received,
 * while previously sent elements **are lost**.
 * Every subscriber immediately receives the most recently sent element.
 * Sender to this broadcast channel never suspends and [trySend] always succeeds.
 *
 * A secondary constructor can be used to create an instance of this class that already holds a value.
 * This channel is also created by `BroadcastChannel(Channel.CONFLATED)` factory function invocation.
 *
 * In this implementation, [opening][openSubscription] and [closing][ReceiveChannel.cancel] subscription
 * takes linear time in the number of subscribers.
 *
 * **Note: This API is obsolete since 1.5.0.** It will be deprecated with warning in 1.7.0
 * and with error in 1.8.0. It is replaced with [StateFlow][kotlinx.coroutines.flow.StateFlow].
 */
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@ObsoleteCoroutinesApi
public class ConflatedBroadcastChannel<E> private constructor(private val broadcast: BroadcastChannelImpl<E>)
    : BroadcastChannel<E> by broadcast
{
    public constructor(): this(BroadcastChannelImpl<E>(capacity = CONFLATED))
    /**
     * Creates an instance of this class that already holds a value.
     *
     * It is as a shortcut to creating an instance with a default constructor and
     * immediately sending an element: `ConflatedBroadcastChannel().apply { offer(value) }`.
     */
    public constructor(value: E) : this() {
        trySend(value)
    }

    /**
     * The most recently sent element to this channel.
     *
     * Access to this property throws [IllegalStateException] when this class is constructed without
     * initial value and no value was sent yet or if it was [closed][close] without a cause.
     * It throws the original [close][SendChannel.close] cause exception if the channel has _failed_.
     */
    public val value: E get() = broadcast.value
    /**
     * The most recently sent element to this channel or `null` when this class is constructed without
     * initial value and no value was sent yet or if it was [closed][close].
     */
    public val valueOrNull: E? get() = broadcast.valueOrNull
}

/**
 * A common implementation for both the broadcast channel with a buffer of fixed [capacity]
 * and the conflated broadcast channel (see [ConflatedBroadcastChannel]).
 *
 * **Note**, that elements that are sent to this channel while there are no
 * [openSubscription] subscribers are immediately lost.
 *
 * This channel is created by `BroadcastChannel(capacity)` factory function invocation.
 */
internal open class BroadcastChannelImpl<E>(
    /**
     * Buffer capacity; [Channel.CONFLATED] when this broadcast is conflated.
     */
    val capacity: Int
) : BufferedChannel<E>(capacity = Channel.RENDEZVOUS, onUndeliveredElement = null), BroadcastChannel<E> {
    init {
        require(capacity >= 1 || capacity == CONFLATED) { "ArrayBroadcastChannel capacity must be at least 1, but $capacity was specified" }
    }

    // All operations are protected by this lock.
    private val lock = ReentrantLock()
    // The list of subscribers; all accesses should be protected by lock.
    private val subscribers: MutableList<BufferedChannel<E>> = mutableListOf()
    // When this broadcast is conflated, this field stores the last sent element.
    // If this channel is empty or not conflated, it stores a special `NO_ELEMENT` marker.
    private var lastConflatedElement: Any? = NO_ELEMENT // NO_ELEMENT or E

    // ###########################
    // # Subscription Management #
    // ###########################

    public override fun openSubscription(): ReceiveChannel<E> = lock.withLock { // protected by lock
        // Is this broadcast conflated or buffered?
        val s = if (capacity == CONFLATED) SubscriberConflated() else SubscriberBuffered()
        if (isClosedForSend && lastConflatedElement === NO_ELEMENT) {
            s.close(getCloseCause())
            return@withLock s
        }
        if (lastConflatedElement !== NO_ELEMENT) {
            s.trySend(value)
        }
        subscribers += s
        s
    }

    private fun removeSubscriber(s: ReceiveChannel<E>) = lock.withLock {
        subscribers.remove(s)
    }

    // #############################
    // # The `send(..)` Operations #
    // #############################

    override suspend fun send(element: E) {
        val subs = lock.withLock { // protected by lock
            if (isClosedForSend) throw sendException(trySend(element).exceptionOrNull())
            if (capacity == CONFLATED)
                lastConflatedElement = element
            ArrayList(subscribers)
        }
        subs.forEach {
            val success = it.sendBroadcast(element)
            if (!success) {
                lock.withLock { // protected by lock
                    if(isClosedForSend) throw sendException(trySend(element).exceptionOrNull())
                }
            }
        }
    }

    override fun trySend(element: E): ChannelResult<Unit> = lock.withLock { // protected by lock
        // Is this channel closed for send?
        if (isClosedForSend) return super.trySend(element)
        // Check whether the plain `send(..)` operation
        // should suspend and fail in this case.
        val shouldSuspend = subscribers.any { it.shouldSendSuspend() }
        if (shouldSuspend) return ChannelResult.failure()
        // Send the element to all subscribers.
        // It is guaranteed that the attempt cannot fail,
        // as both the broadcast closing and subscription
        // cancellation are guarded by lock, which is held
        // by the current operation.
        subscribers.forEach { it.trySend(element) }
        // Update the last sent element if this broadcast is conflated.
        if (capacity == CONFLATED) lastConflatedElement = element
        // Finish with success.
        return ChannelResult.success(Unit)
    }

    // ###########################################
    // # The `select` Expression: onSend { ... } #
    // ###########################################

    override fun registerSelectForSend(select: SelectInstance<*>, element: Any?) {
        element as E
        lock.withLock { // protected by lock
            val result = onSendStatus.remove(select)
            if (result != null) {
                select.selectInRegistrationPhase(result)
                return
            }
        }
        CoroutineScope(select.context).launch(start = CoroutineStart.UNDISPATCHED) {
            val success: Boolean = try {
                send(element)
                true
            } catch (t: Throwable) {
                // closed
                false
            }
            lock.withLock {
                check(onSendStatus[select] == null)
                onSendStatus[select] = if (success) Unit else CHANNEL_CLOSED
                select as SelectImplementation<*>
                val trySelectResult = select.trySelectDetailed(this@BroadcastChannelImpl,  Unit)
                if (trySelectResult !== TrySelectDetailedResult.REREGISTER) {
                    onSendStatus.remove(select)
                }
            }

        }
    }
    private val onSendStatus = HashMap<SelectInstance<*>, Any?>() // select -> Unit or CHANNEL_CLOSED

    // ############################
    // # Closing and Cancellation #
    // ############################

    override fun close(cause: Throwable?): Boolean = lock.withLock { // protected by lock
        // Close all subscriptions first.
        subscribers.forEach { it.close(cause) }
        // Remove all subscriptions that do not contain
        // buffered elements or waiting send-s to avoid
        // memory leaks. We must keep other subscriptions
        // in case `broadcast.cancel(..)` is called.
        subscribers.removeAll { !it.hasElements() }
        // Delegate to the parent implementation.
        super.close(cause)
    }

    override fun cancelImpl(cause: Throwable?): Boolean = lock.withLock { // protected by lock
        // Cancel all subscriptions. As part of cancellation procedure,
        // subscriptions automatically remove themselves from this broadcast.
        ArrayList(subscribers).forEach { it.cancelImpl(cause) }
        // For the conflated implementation, clear the last sent element.
        lastConflatedElement = NO_ELEMENT
        // Finally, delegate to the parent implementation.
        super.cancelImpl(cause)
    }

    override val isClosedForSend: Boolean
        // Protect by lock to synchronize with `close(..)` / `cancel(..)`.
        get() = lock.withLock { super.isClosedForSend }

    // ##############################
    // # Subscriber Implementations #
    // ##############################

    private inner class SubscriberBuffered : BufferedChannel<E>(capacity = capacity) {
        public override fun cancelImpl(cause: Throwable?): Boolean = lock.withLock {
            // Remove this subscriber from the broadcast on cancellation.
            removeSubscriber(this@SubscriberBuffered )
            super.cancelImpl(cause)
        }
    }

    private inner class SubscriberConflated : ConflatedBufferedChannel<E>(capacity = 1, onBufferOverflow = DROP_OLDEST) {
        public override fun cancelImpl(cause: Throwable?): Boolean {
            // Remove this subscriber from the broadcast on cancellation.
            removeSubscriber(this@SubscriberConflated )
            return super.cancelImpl(cause)
        }
    }

    // ########################################
    // # ConflatedBroadcastChannel Operations #
    // ########################################

    @Suppress("UNCHECKED_CAST")
    val value: E get() = lock.withLock {
        // Is this channel closed for sending?
        if (isClosedForSend) {
            throw getCloseCause() ?: IllegalStateException("This broadcast channel is closed")
        }
        // Is there sent element?
        if (lastConflatedElement === NO_ELEMENT) error("No value")
        // Return the last sent element.
        lastConflatedElement as E
    }

    val valueOrNull: E? get() = lock.withLock {
        // Is this channel closed for sending?
        if (isClosedForReceive) null
        // Is there sent element?
        else if (lastConflatedElement === NO_ELEMENT) null
        // Return the last sent element.
        else lastConflatedElement as E
    }

    // #################
    // # For Debugging #
    // #################

    override fun toString() =
        (if (lastConflatedElement !== NO_ELEMENT) "CONFLATED_ELEMENT=$lastConflatedElement; " else "") +
            "BROADCAST=<${super.toString()}>; " +
            "SUBSCRIBERS=${subscribers.joinToString(separator = ";", prefix = "<", postfix = ">")}"
}

@SharedImmutable
private val NO_ELEMENT = Symbol("NO_ELEMENT")