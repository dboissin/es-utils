package dev.ceven.testbench.domain.mapping;

import dev.ceven.testbench.domain.specification.Specification;

public class FieldRule<S, F> {
    private final Specification<S> specification;
    private final Mapper<S, F> mapper;

    public FieldRule(Specification<S> specification, Mapper<S, F> mapper) {
        this.specification = specification;
        this.mapper = mapper;
    }

    public boolean isSatisfiedBy(S source) {
        return specification.isSatisfiedBy(source);
    }

    public F apply(S source) {
        return mapper.map(source);
    }
}
