package com.designpattern.cognitorbac.audit;

import com.designpattern.cognitorbac.exception.AuditException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Composable audit query service backed by {@link MongoTemplate}.
 *
 * <p>Every field in {@link AuditFilter} is optional — only non-null values
 * become query criteria, so any combination of filters works without
 * writing a dedicated repository method for each combination.</p>
 *
 * <p>Indexed fields used by criteria:
 * <ul>
     *   <li>{@code role_name}   — equality</li>
     *   <li>{@code user_sub}    — equality</li>
 *   <li>{@code action}      — $in list</li>
 *   <li>{@code occurred_at} — range ($gte / $lte)</li>
 *   <li>{@code changes.field} — element match (array field, no separate index needed for MVP)</li>
 * </ul>
 * </p>
 */
@Service
public class AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditQueryService.class);

    private final MongoTemplate mongo;

    public AuditQueryService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Executes a composable, paginated query against {@code audit_entries}.
     * Null filter fields are ignored — they do not restrict results.
     *
     * @param filter   the filter criteria; pass {@code AuditFilter.builder().build()} for no filters
     * @param pageable sorting and pagination
     * @return a page of matching audit entries
     */
    public Page<AuditEntry> search(AuditFilter filter, Pageable pageable) {
        if (filter.getFrom() != null && filter.getTo() != null
                && filter.getFrom().isAfter(filter.getTo())) {
            throw new IllegalArgumentException(
                    "'from' must not be after 'to': from=" + filter.getFrom() + " to=" + filter.getTo());
        }
        try {
            Query query = buildQuery(filter);
            long total = mongo.count(query, AuditEntry.class);
            query.with(pageable);
            List<AuditEntry> results = mongo.find(query, AuditEntry.class);
            return new PageImpl<>(results, pageable, total);
        } catch (DataAccessException ex) {
            log.error("MongoDB query failed for audit search filter={}", filter, ex);
            throw new AuditException("Audit query failed — storage unavailable", ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during audit search", ex);
            throw new AuditException("Audit query encountered an unexpected error", ex);
        }
    }

    private Query buildQuery(AuditFilter f) {
        List<Criteria> criteria = new ArrayList<>();

        if (f.getRoleName() != null && !f.getRoleName().isBlank()) {
            criteria.add(Criteria.where("role_name").is(f.getRoleName()));
        }
        if (f.getUserSub() != null && !f.getUserSub().isBlank()) {
            criteria.add(Criteria.where("user_sub").is(f.getUserSub()));
        }
        if (f.getUserEmail() != null && !f.getUserEmail().isBlank()) {
            criteria.add(Criteria.where("user_email").is(f.getUserEmail()));
        }
        if (f.getRoleKey() != null && !f.getRoleKey().isBlank()) {
            criteria.add(Criteria.where("role_key").is(f.getRoleKey()));
        }
        if (f.getPermissionId() != null && !f.getPermissionId().isBlank()) {
            criteria.add(Criteria.where("permission_id").is(f.getPermissionId()));
        }
        if (f.getAggregateType() != null && !f.getAggregateType().isBlank()) {
            criteria.add(Criteria.where("aggregate_type").is(f.getAggregateType()));
        }
        if (f.getAggregateId() != null && !f.getAggregateId().isBlank()) {
            criteria.add(Criteria.where("aggregate_id").is(f.getAggregateId()));
        }
        if (f.getActions() != null && !f.getActions().isEmpty()) {
            criteria.add(Criteria.where("action").in(f.getActions()));
        }
        if (f.getChangedField() != null && !f.getChangedField().isBlank()) {
            criteria.add(Criteria.where("changes").elemMatch(
                    Criteria.where("field").is(f.getChangedField())
            ));
        }
        if (f.getFrom() != null) {
            criteria.add(Criteria.where("occurred_at").gte(f.getFrom()));
        }
        if (f.getTo() != null) {
            criteria.add(Criteria.where("occurred_at").lte(f.getTo()));
        }

        if (criteria.isEmpty()) {
            return new Query();
        }
        return new Query(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }
}
