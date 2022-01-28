/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package kotlinx.coroutines.channels

import kotlinx.coroutines.*
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
        BUFFERED -> BufferedBroadcastChannel(CHANNEL_DEFAULT_CAPACITY)
        else -> BufferedBroadcastChannel(capacity)
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
 * **Note: This API is obsolete since 1.5.0.** It will be deprecated with warning in 1.6.0
 * and with error in 1.7.0. It is replaced with [StateFlow][kotlinx.coroutines.flow.StateFlow].
 */
@ObsoleteCoroutinesApi
internal class ConflatedBroadcastChannel<E>() :
    BufferedBroadcastChannel<E>(capacity = CONFLATED),
    BroadcastChannel<E>
{
    /**
     * Creates an instance of this class that already holds a value.
     *
     * It is as a shortcut to creating an instance with a default constructor and
     * immediately sending an element: `ConflatedBroadcastChannel().apply { offer(value) }`.
     */
    public constructor(value: E) : this() {
        trySend(value)
    }

    override fun registerSelectForSend(select: SelectInstance<*>, element: Any?) {
        trySend(element as E)
            .onSuccess { select.selectInRegistrationPhase(Unit) }
            .onClosed { select.selectInRegistrationPhase(CHANNEL_CLOSED) }
    }
}

/**
 * Broadcast channel with array buffer of a fixed [capacity].
 * Sender suspends only when buffer is full due to one of the receives being slow to consume and
 * receiver suspends only when buffer is empty.
 *
 * **Note**, that elements that are sent to this channel while there are no
 * [openSubscription] subscribers are immediately lost.
 *
 * This channel is created by `BroadcastChannel(capacity)` factory function invocation.
 */
internal open class BufferedBroadcastChannel<E>(
    /**
     * Buffer capacity.
     */
    val capacity: Int
) : BufferedChannel<E>(capacity = Channel.RENDEZVOUS, onUndeliveredElement = null), BroadcastChannel<E> {
    init {
        require(capacity >= 1 || capacity == CONFLATED) { "ArrayBroadcastChannel capacity must be at least 1, but $capacity was specified" }
    }

    private val lock = ReentrantLock()
    private val subscribers: MutableList<BufferedChannel<E>> = mutableListOf()

    private var lastConflatedElement: Any? = NO_ELEMENT // NO_ELEMENT or E

    /**
     * The most recently sent element to this channel.
     *
     * Access to this property throws [IllegalStateException] when this class is constructed without
     * initial value and no value was sent yet or if it was [closed][close] without a cause.
     * It throws the original [close][SendChannel.close] cause exception if the channel has _failed_.
     */
    @Suppress("UNCHECKED_CAST")
    public val value: E get() = lock.withLock {
        if (isClosedForReceive) {
            throw closeCause2 ?: IllegalStateException("This broadcast channel is closed")
        }
        lastConflatedElement.let {
            if (it !== NO_ELEMENT) it as E
            else error("No value")
        }
    }

    /**
     * The most recently sent element to this channel or `null` when this class is constructed without
     * initial value and no value was sent yet or if it was [closed][close].
     */
    public val valueOrNull: E? get() = lock.withLock {
        if (isClosedForReceive) null
        else if (lastConflatedElement === NO_ELEMENT) null
        else lastConflatedElement as E
    }

    public override fun openSubscription(): ReceiveChannel<E> = lock.withLock {
        val s = if (capacity == CONFLATED) SubscriberConflated() else SubscriberBuffered()
        if (isClosedForSend && lastConflatedElement === NO_ELEMENT) {
            s.close(closeCause2)
            return@withLock s
        }
        if (lastConflatedElement !== NO_ELEMENT) {
            s.trySend(value)
        }
        subscribers += s
        s
    }

    private fun removeSubscriberUsync(s: ReceiveChannel<E>) {
        subscribers.remove(s)
    }

    override suspend fun send(element: E) {
        val subs = lock.withLock {
            if (isClosedForSend) throw sendException(trySend(element).exceptionOrNull())
            if (capacity == CONFLATED)
                lastConflatedElement = element
            ArrayList(subscribers)
        }
        subs.forEach {
            val success = it.sendBroadcast(element)
            if (!success) {
                lock.withLock {
                    if(isClosedForSend) throw sendException(trySend(element).exceptionOrNull())
                }
            }
        }
    }

    override fun trySend(element: E): ChannelResult<Unit> = lock.withLock {
        if (isClosedForSend) return super.trySend(element)
        val success = capacity == CONFLATED || subscribers.none { it.shouldSendSuspend() }
        if (!success) {
            return ChannelResult.failure()
        }
        subscribers.forEach { it.trySend(element) }
        if (capacity == CONFLATED)
            lastConflatedElement = element
        return ChannelResult.success(Unit)
    }

    override fun registerSelectForSend(select: SelectInstance<*>, element: Any?) {
        element as E
        lock.withLock {
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
                val trySelectResult = select.trySelectDetailed(this@BufferedBroadcastChannel,  Unit)
                if (trySelectResult !== TrySelectDetailedResult.REREGISTER) {
                    onSendStatus.remove(select)
                }
            }

        }
    }
    private val onSendStatus = HashMap<SelectInstance<*>, Any?>() // select -> Unit or CHANNEL_CLOSED

    override fun close(cause: Throwable?): Boolean = lock.withLock {
        subscribers.forEach { it.close(cause) }
        subscribers.removeAll { !it.hasElements() }
        return super.close(cause)
    }

    override fun cancelImpl(cause: Throwable?): Boolean = lock.withLock {
        super.cancelImpl(cause).also {
            subscribers.forEach {
                it as Subscriber<E>
                it.cancelImplWithoutRemovingSubscriber(cause)
            }
            subscribers.clear()
            lastConflatedElement = NO_ELEMENT
        }
    }

    override val isClosedForSend: Boolean
        get() = lock.withLock { super.isClosedForSend }

    private interface Subscriber<E> : ReceiveChannel<E> {
        fun cancelImplWithoutRemovingSubscriber(cause: Throwable?)
    }

    private inner class SubscriberBuffered : BufferedChannel<E>(capacity = capacity), Subscriber<E> {
        override fun tryReceive(): ChannelResult<E> {
            lock.lock()
            return super.tryReceive()
        }

        override suspend fun receive(): E {
            lock.lock()
            return super.receive()
        }

        override suspend fun receiveCatching(): ChannelResult<E> {
            lock.lock()
            return super.receiveCatching()
        }

        override fun registerSelectForReceive(select: SelectInstance<*>, ignoredParam: Any?) {
            lock.lock()
            super.registerSelectForReceive(select, ignoredParam)
        }

        override fun iterator() = SubscriberIterator()

        override fun onReceiveSynchronizationCompletion() {
            super.onReceiveSynchronizationCompletion()
            lock.unlock()
        }

        override val isClosedForReceive: Boolean
            get() = lock.withLock { super.isClosedForReceive }

        override val isEmpty: Boolean
            get() = lock.withLock { super.isEmpty }

        public override fun cancelImpl(cause: Throwable?): Boolean = lock.withLock {
            removeSubscriberUsync(this@SubscriberBuffered )
            super.cancelImpl(cause)
        }

        override fun cancelImplWithoutRemovingSubscriber(cause: Throwable?) {
            super.cancelImpl(cause)
        }

        private inner class SubscriberIterator : BufferedChannelIterator() {
            override suspend fun hasNext(): Boolean {
                lock.lock()
                return super.hasNext()
            }
        }
    }

    private inner class SubscriberConflated
        : ConflatedBufferedChannel<E>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST), Subscriber<E>
    {
        override fun tryReceive(): ChannelResult<E> {
            lock.lock()
            return super.tryReceive()
        }

        override suspend fun receive(): E {
            lock.lock()
            return super.receive()
        }

        override suspend fun receiveCatching(): ChannelResult<E> {
            lock.lock()
            return super.receiveCatching()
        }

        override fun registerSelectForReceive(select: SelectInstance<*>, ignoredParam: Any?) {
            lock.lock()
            super.registerSelectForReceive(select, ignoredParam)
        }

        override fun iterator() = SubscriberIterator()

        override fun onReceiveSynchronizationCompletion() {
            super.onReceiveSynchronizationCompletion()
            lock.unlock()
        }

        override val isClosedForReceive: Boolean
            get() = lock.withLock { super.isClosedForReceive }

        override val isEmpty: Boolean
            get() = lock.withLock { super.isEmpty }

        public override fun cancelImpl(cause: Throwable?): Boolean = lock.withLock {
            removeSubscriberUsync(this@SubscriberConflated )
            super.cancelImpl(cause)
        }

        override fun cancelImplWithoutRemovingSubscriber(cause: Throwable?) {
            super.cancelImpl(cause)
        }

        private inner class SubscriberIterator : ConflatedChannelIterator() {
            override suspend fun hasNext(): Boolean {
                lock.lock()
                return super.hasNext()
            }
        }
    }

    override fun toString() =
        (if (lastConflatedElement !== NO_ELEMENT) "CONFLATED_ELEMENT=$lastConflatedElement; " else "") +
            "BROADCAST=<${super.toString()}>; " +
            "SUBSCRIBERS=${subscribers.joinToString(separator = ";", prefix = "<", postfix = ">")}"
}

@SharedImmutable
private val NO_ELEMENT = Symbol("NO_ELEMENT")
