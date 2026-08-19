package me.lucko.spark.common.sampler;

import java.util.function.Supplier;

public interface Sampler {
    enum SamplerType {
        JAVA,
        ASYNC
    }

    enum SamplerMode {
        EXECUTION,
        ALLOCATION
    }

    final class ExportProps {
        private Supplier<?> classSourceLookup;

        public ExportProps() {
        }

        public ExportProps classSourceLookup(Supplier<?> classSourceLookup) {
            this.classSourceLookup = classSourceLookup;
            return this;
        }

        public Supplier<?> classSourceLookup() {
            return classSourceLookup;
        }
    }
}
