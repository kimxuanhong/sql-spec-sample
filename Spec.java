import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent API builder for JPA Specifications with support for complex queries
 * @param <T> Entity type
 */
public class Spec<T> {

    private Specification<T> spec;
    private final Map<String, Join<?, ?>> joinCache = new HashMap<>();

    private Spec() {
        spec = (root, query, cb) -> cb.conjunction();
    }

    public static <T> Spec<T> of(Class<T> clazz) {
        return new Spec<>();
    }

    // ===== LOGIC OPERATORS =====
    
    public Spec<T> and(Specification<T> other) {
        if (other != null) {
            spec = spec.and(other);
        }
        return this;
    }

    public Spec<T> or(Specification<T> other) {
        if (other != null) {
            spec = spec.or(other);
        }
        return this;
    }

    public Spec<T> not(Specification<T> other) {
        if (other != null) {
            spec = spec.and(Specification.not(other));
        }
        return this;
    }

    // ===== BASIC PREDICATES =====
    
    public Spec<T> eq(String field, Object value) {
        if (value == null) return this;
        spec = spec.and((root, query, cb) -> cb.equal(getPath(root, field), value));
        return this;
    }

    public Spec<T> ne(String field, Object value) {
        if (value == null) return this;
        spec = spec.and((root, query, cb) -> cb.notEqual(getPath(root, field), value));
        return this;
    }

    public Spec<T> isNull(String field) {
        spec = spec.and((root, query, cb) -> cb.isNull(getPath(root, field)));
        return this;
    }

    public Spec<T> isNotNull(String field) {
        spec = spec.and((root, query, cb) -> cb.isNotNull(getPath(root, field)));
        return this;
    }

    public Spec<T> like(String field, String value) {
        if (value == null || value.isBlank()) return this;
        spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(getPath(root, field)), "%" + value.toLowerCase() + "%"));
        return this;
    }

    public Spec<T> startsWith(String field, String value) {
        if (value == null || value.isBlank()) return this;
        spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(getPath(root, field)), value.toLowerCase() + "%"));
        return this;
    }

    public Spec<T> endsWith(String field, String value) {
        if (value == null || value.isBlank()) return this;
        spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(getPath(root, field)), "%" + value.toLowerCase()));
        return this;
    }

    // ===== COMPARISON PREDICATES =====
    
    public Spec<T> gt(String field, Comparable<?> value) {
        if (value == null) return this;
        spec = spec.and((root, query, cb) -> {
            @SuppressWarnings("unchecked")
            Expression<Comparable> expr = (Expression<Comparable>) getPath(root, field);
            return cb.greaterThan(expr, (Comparable) value);
        });
        return this;
    }

    public Spec<T> gte(String field, Comparable<?> value) {
        if (value == null) return this;
        spec = spec.and((root, query, cb) -> {
            @SuppressWarnings("unchecked")
            Expression<Comparable> expr = (Expression<Comparable>) getPath(root, field);
            return cb.greaterThanOrEqualTo(expr, (Comparable) value);
        });
        return this;
    }

    public Spec<T> lt(String field, Comparable<?> value) {
        if (value == null) return this;
        spec = spec.and((root, query, cb) -> {
            @SuppressWarnings("unchecked")
            Expression<Comparable> expr = (Expression<Comparable>) getPath(root, field);
            return cb.lessThan(expr, (Comparable) value);
        });
        return this;
    }

    public Spec<T> lte(String field, Comparable<?> value) {
        if (value == null) return this;
        spec = spec.and((root, query, cb) -> {
            @SuppressWarnings("unchecked")
            Expression<Comparable> expr = (Expression<Comparable>) getPath(root, field);
            return cb.lessThanOrEqualTo(expr, (Comparable) value);
        });
        return this;
    }

    public Spec<T> between(String field, Comparable<?> start, Comparable<?> end) {
        if (start == null || end == null) return this;

        spec = spec.and((root, query, cb) -> {
            Path<?> path = getPath(root, field);

            if (!Comparable.class.isAssignableFrom(path.getJavaType())) {
                throw new IllegalArgumentException("Field '" + field + "' is not Comparable: " + path.getJavaType());
            }

            @SuppressWarnings("unchecked")
            Expression<? extends Comparable> expr = (Expression<? extends Comparable>) path;

            return cb.between(expr, (Comparable) start, (Comparable) end);
        });

        return this;
    }

    public Spec<T> in(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        spec = spec.and((root, query, cb) -> getPath(root, field).in(values));
        return this;
    }

    public Spec<T> notIn(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) return this;
        spec = spec.and((root, query, cb) -> cb.not(getPath(root, field).in(values)));
        return this;
    }

    // ===== BOOLEAN PREDICATES =====
    
    public Spec<T> isTrue(String field) {
        spec = spec.and((root, query, cb) -> cb.isTrue(getPath(root, field)));
        return this;
    }

    public Spec<T> isFalse(String field) {
        spec = spec.and((root, query, cb) -> cb.isFalse(getPath(root, field)));
        return this;
    }

    // ===== JOIN DSL =====
    
    public Spec<T> join(String joinField, Consumer<SpecJoin<T>> joinConsumer) {
        return join(joinField, JoinType.INNER, joinConsumer);
    }

    public Spec<T> leftJoin(String joinField, Consumer<SpecJoin<T>> joinConsumer) {
        return join(joinField, JoinType.LEFT, joinConsumer);
    }

    public Spec<T> rightJoin(String joinField, Consumer<SpecJoin<T>> joinConsumer) {
        return join(joinField, JoinType.RIGHT, joinConsumer);
    }

    public Spec<T> join(String joinField, JoinType type, Consumer<SpecJoin<T>> joinConsumer) {
        if (joinConsumer == null) return this;
        SpecJoin<T> joinBuilder = new SpecJoin<>(joinField, type);
        joinConsumer.accept(joinBuilder);
        spec = spec.and(joinBuilder.build());
        return this;
    }

    // ===== BUILD =====
    
    public Specification<T> build() {
        return spec;
    }

    // ===== PATH HELPER =====
    
    @SuppressWarnings("unchecked")
    private <Y> Path<Y> getPath(Root<T> root, String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Field name cannot be null or blank");
        }
        
        if (!field.contains(".")) {
            return root.get(field);
        }
        
        String[] parts = field.split("\\.");
        Path<?> path = root;
        for (String part : parts) {
            path = path.get(part);
        }
        return (Path<Y>) path;
    }

    // ===== NESTED JOIN BUILDER =====
    
    public static class SpecJoin<T> {

        private final String joinField;
        private final JoinType joinType;
        private Specification<T> spec;
        private final Map<String, Join<?, ?>> joinCache = new HashMap<>();

        public SpecJoin(String joinField, JoinType joinType) {
            this.joinField = joinField;
            this.joinType = joinType;
            this.spec = (root, query, cb) -> cb.conjunction();
        }

        public SpecJoin<T> eq(String field, Object value) {
            if (value == null) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                return cb.equal(join.get(field), value);
            });
            return this;
        }

        public SpecJoin<T> ne(String field, Object value) {
            if (value == null) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                return cb.notEqual(join.get(field), value);
            });
            return this;
        }

        public SpecJoin<T> isNull(String field) {
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                return cb.isNull(join.get(field));
            });
            return this;
        }

        public SpecJoin<T> isNotNull(String field) {
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                return cb.isNotNull(join.get(field));
            });
            return this;
        }

        public SpecJoin<T> like(String field, String value) {
            if (value == null || value.isBlank()) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                return cb.like(cb.lower(join.get(field)), "%" + value.toLowerCase() + "%");
            });
            return this;
        }

        public SpecJoin<T> gt(String field, Comparable<?> value) {
            if (value == null) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                @SuppressWarnings("unchecked")
                Expression<Comparable> expr = (Expression<Comparable>) join.get(field);
                return cb.greaterThan(expr, (Comparable) value);
            });
            return this;
        }

        public SpecJoin<T> gte(String field, Comparable<?> value) {
            if (value == null) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                @SuppressWarnings("unchecked")
                Expression<Comparable> expr = (Expression<Comparable>) join.get(field);
                return cb.greaterThanOrEqualTo(expr, (Comparable) value);
            });
            return this;
        }

        public SpecJoin<T> lt(String field, Comparable<?> value) {
            if (value == null) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                @SuppressWarnings("unchecked")
                Expression<Comparable> expr = (Expression<Comparable>) join.get(field);
                return cb.lessThan(expr, (Comparable) value);
            });
            return this;
        }

        public SpecJoin<T> lte(String field, Comparable<?> value) {
            if (value == null) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                @SuppressWarnings("unchecked")
                Expression<Comparable> expr = (Expression<Comparable>) join.get(field);
                return cb.lessThanOrEqualTo(expr, (Comparable) value);
            });
            return this;
        }

        public SpecJoin<T> in(String field, Collection<?> values) {
            if (values == null || values.isEmpty()) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                return join.get(field).in(values);
            });
            return this;
        }

        public SpecJoin<T> notIn(String field, Collection<?> values) {
            if (values == null || values.isEmpty()) return this;
            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                return cb.not(join.get(field).in(values));
            });
            return this;
        }

        public SpecJoin<T> between(String field, Comparable<?> start, Comparable<?> end) {
            if (start == null || end == null) return this;

            spec = spec.and((root, query, cb) -> {
                Join<Object, Object> join = getOrCreateJoin(root);
                Path<?> path = join.get(field);

                if (!Comparable.class.isAssignableFrom(path.getJavaType())) {
                    throw new IllegalArgumentException("Join field '" + field + "' is not Comparable: " + path.getJavaType());
                }

                @SuppressWarnings("unchecked")
                Expression<? extends Comparable> expr = (Expression<? extends Comparable>) path;

                return cb.between(expr, (Comparable) start, (Comparable) end);
            });

            return this;
        }

        @SuppressWarnings("unchecked")
        private Join<Object, Object> getOrCreateJoin(Root<T> root) {
            return (Join<Object, Object>) joinCache.computeIfAbsent(
                joinField, 
                k -> root.join(joinField, joinType)
            );
        }

        public Specification<T> build() {
            return spec;
        }
    }
}