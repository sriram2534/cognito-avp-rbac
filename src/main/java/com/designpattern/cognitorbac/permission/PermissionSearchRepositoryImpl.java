package com.designpattern.cognitorbac.permission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** MongoDB implementation that applies permission filters before pagination. */
public class PermissionSearchRepositoryImpl implements PermissionSearchRepository {
    private final MongoTemplate mongoTemplate;

    public PermissionSearchRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Permission> search(PermissionFilter filter, Pageable pageable) {
        Criteria criteria = criteria(filter);
        long total = mongoTemplate.count(Query.query(criteria), Permission.class);
        List<Permission> content = total == 0 ? List.of()
                : mongoTemplate.find(Query.query(criteria).with(pageable), Permission.class);
        return new PageImpl<>(content, pageable, total);
    }

    private Criteria criteria(PermissionFilter filter) {
        List<Criteria> filters = new ArrayList<>();
        addExact(filters, "module", filter.module());
        addExact(filters, "resourceType", filter.resourceType());
        addExact(filters, "access", filter.access());
        if (filter.status() != null) {
            filters.add(Criteria.where("status").is(filter.status()));
        }
        if (filter.search() != null) {
            Pattern search = Pattern.compile(Pattern.quote(filter.search()),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filters.add(new Criteria().orOperator(
                    Criteria.where("module").regex(search),
                    Criteria.where("resourceType").regex(search),
                    Criteria.where("access").regex(search),
                    Criteria.where("description").regex(search)));
        }
        return filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters);
    }

    private void addExact(List<Criteria> filters, String field, String value) {
        if (value != null) {
            filters.add(Criteria.where(field).is(value));
        }
    }
}
