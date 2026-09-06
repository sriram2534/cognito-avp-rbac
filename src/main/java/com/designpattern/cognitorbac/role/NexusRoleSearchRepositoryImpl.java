package com.designpattern.cognitorbac.role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** MongoDB implementation that applies role filters before pagination. */
public class NexusRoleSearchRepositoryImpl implements NexusRoleSearchRepository {
    private final MongoTemplate mongoTemplate;

    public NexusRoleSearchRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<NexusRole> search(RoleFilter filter, Pageable pageable) {
        Criteria criteria = criteria(filter);
        long total = mongoTemplate.count(Query.query(criteria), NexusRole.class);
        List<NexusRole> content = total == 0 ? List.of()
                : mongoTemplate.find(Query.query(criteria).with(pageable), NexusRole.class);
        return new PageImpl<>(content, pageable, total);
    }

    private Criteria criteria(RoleFilter filter) {
        List<Criteria> filters = new ArrayList<>();
        if (filter.module() != null) {
            filters.add(Criteria.where("module").is(filter.module()));
        }
        if (filter.status() != null) {
            filters.add(Criteria.where("status").is(filter.status()));
        }
        if (filter.search() != null) {
            Pattern search = Pattern.compile(Pattern.quote(filter.search()),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filters.add(new Criteria().orOperator(
                    Criteria.where("roleKey").regex(search),
                    Criteria.where("name").regex(search),
                    Criteria.where("displayName").regex(search),
                    Criteria.where("description").regex(search)));
        }
        return filters.isEmpty() ? new Criteria() : new Criteria().andOperator(filters);
    }
}
