package dev.ceven.testbench.domain.mapping;

@FunctionalInterface
public interface Mapper<S, T> {
    T map(S source);
}
