package com.tokenfest.gateway.support;

import java.util.function.BooleanSupplier;

/** Polls until a condition is true, for things that happen in the background. */
public final class Waits {

	private Waits() {
	}

	public static void until(BooleanSupplier condition) {
		long deadline = System.currentTimeMillis() + 5000;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				throw new AssertionError("Condition not met within 5 seconds");
			}
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
		}
	}

}
