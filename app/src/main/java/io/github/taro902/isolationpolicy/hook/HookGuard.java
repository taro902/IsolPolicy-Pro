package io.github.taro902.isolationpolicy.hook;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.Logger;

public final class HookGuard {

    public static final int DEFAULT_TRIP_THRESHOLD = 8;

    public interface Handler {
        Object handle(XposedInterface.Chain chain) throws Throwable;
    }

    public interface PureCall {
        Object call() throws Throwable;
    }

    public interface FaultListener {
        void onFault(int pillar, int consecutiveFaults, boolean tripped);
    }

    private static final class ProceedSlot {
        boolean proceeded;
        Object result;
    }

    private static final class ProceedException extends RuntimeException {
        final Throwable causeThrowable;

        ProceedException(Throwable causeThrowable) {
            super(null, null, false, false);
            this.causeThrowable = causeThrowable;
        }
    }

    private static final ThreadLocal<ProceedSlot> PROCEED_SLOT = new ThreadLocal<ProceedSlot>() {
        @Override
        protected ProceedSlot initialValue() {
            return new ProceedSlot();
        }
    };

    private final int pillar;
    private final String label;
    private final int tripThreshold;
    private final FaultListener faultListener;
    private final AtomicInteger consecutiveFaults = new AtomicInteger(0);
    private final AtomicInteger totalFaults = new AtomicInteger(0);
    private volatile boolean tripped = false;

    public HookGuard(int pillar, String label, FaultListener faultListener) {
        this(pillar, label, DEFAULT_TRIP_THRESHOLD, faultListener);
    }

    public HookGuard(int pillar, String label, int tripThreshold, FaultListener faultListener) {
        this.pillar = pillar;
        this.label = label != null ? label : "HOOK";
        this.tripThreshold = Math.max(1, tripThreshold);
        this.faultListener = faultListener;
    }

    public static Object proceedOriginal(XposedInterface.Chain chain) throws Throwable {
        ProceedSlot slot = PROCEED_SLOT.get();
        try {
            Object res = chain.proceed();
            slot.result = res;
            slot.proceeded = true;
            return res;
        } catch (Throwable t) {
            slot.proceeded = true;
            slot.result = null;
            throw new ProceedException(t);
        }
    }

    public static Object proceedWithArgs(XposedInterface.Chain chain, Object[] args) throws Throwable {
        ProceedSlot slot = PROCEED_SLOT.get();
        try {
            Object res = chain.proceed(args);
            slot.result = res;
            slot.proceeded = true;
            return res;
        } catch (Throwable t) {
            slot.proceeded = true;
            slot.result = null;
            throw new ProceedException(t);
        }
    }

    public XposedInterface.Hooker wrap(final Handler handler) {
        return new XposedInterface.Hooker() {
            @Override
            public Object intercept(XposedInterface.Chain chain) throws Throwable {
                if (tripped) {
                    return chain.proceed();
                }
                ProceedSlot slot = PROCEED_SLOT.get();
                boolean prevProceeded = slot.proceeded;
                Object prevResult = slot.result;
                slot.proceeded = false;
                slot.result = null;
                try {
                    Object res = handler.handle(chain);
                    if (consecutiveFaults.get() != 0) {
                        consecutiveFaults.set(0);
                    }
                    return res;
                } catch (ProceedException pe) {
                    throw pe.causeThrowable;
                } catch (Throwable t) {
                    recordInternalFault(t);
                    if (slot.proceeded) {
                        return slot.result;
                    }
                    return chain.proceed();
                } finally {
                    slot.proceeded = prevProceeded;
                    slot.result = prevResult;
                }
            }
        };
    }

    public Object executePure(PureCall guardedBody, PureCall passthroughFallback) throws Throwable {
        if (tripped) {
            return passthroughFallback.call();
        }
        try {
            Object res = guardedBody.call();
            if (consecutiveFaults.get() != 0) {
                consecutiveFaults.set(0);
            }
            return res;
        } catch (ProceedException pe) {
            throw pe.causeThrowable;
        } catch (Throwable t) {
            recordInternalFault(t);
            return passthroughFallback.call();
        }
    }

    public static Object invokePureTarget(PureCall targetCall) throws Throwable {
        try {
            return targetCall.call();
        } catch (Throwable t) {
            throw new ProceedException(t);
        }
    }

    private void recordInternalFault(Throwable t) {
        int streak = consecutiveFaults.incrementAndGet();
        totalFaults.incrementAndGet();
        boolean nowTripped = false;
        if (streak >= tripThreshold && !tripped) {
            tripped = true;
            nowTripped = true;
        }
        if (streak == 1 || nowTripped) {
            Logger.e(label + " fault=" + streak + (nowTripped ? " TRIPPED" : ""), t);
        }
        if (faultListener != null) {
            try {
                faultListener.onFault(pillar, streak, nowTripped);
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isTripped() {
        return tripped;
    }

    public int getConsecutiveFaults() {
        return consecutiveFaults.get();
    }

    public int getTotalFaults() {
        return totalFaults.get();
    }

    public int getPillar() {
        return pillar;
    }

    public void resetForTest() {
        consecutiveFaults.set(0);
        totalFaults.set(0);
        tripped = false;
    }
}
