package dev.zenith.trader.module;

/*
 * @author IceTank
 * @since 20.08.2025
 */
public interface IStatemachine {
    void onTick();
    void reset();
    boolean isSuccessful();
    boolean isError();
    boolean isRunning();
}
