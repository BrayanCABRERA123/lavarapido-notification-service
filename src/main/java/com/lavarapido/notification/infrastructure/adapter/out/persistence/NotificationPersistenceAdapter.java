package com.lavarapido.notification.infrastructure.adapter.out.persistence;

import com.lavarapido.notification.domain.exception.UnknownNotificationTypeException;
import com.lavarapido.notification.domain.model.Notification;
import com.lavarapido.notification.domain.model.NotificationFilter;
import com.lavarapido.notification.domain.model.NotificationTypeCode;
import com.lavarapido.notification.domain.model.PageResult;
import com.lavarapido.notification.domain.port.out.NotificationRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.entity.NotificationTypeJpaEntity;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.NotificationJpaRepository;
import com.lavarapido.notification.infrastructure.adapter.out.persistence.repository.NotificationTypeJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Traduce entre el dominio y las tablas. El tipo se guarda por id (FK) pero el dominio trabaja
 * con el code: el catálogo es pequeño y fijo, así que se carga una vez y queda en memoria.
 */
@Component
class NotificationPersistenceAdapter implements NotificationRepository {

    // los filtros de fecha de las pantallas son días en hora de Colombia
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    private final NotificationJpaRepository notifications;
    private final NotificationTypeJpaRepository types;
    private final Map<NotificationTypeCode, Short> idByCode = new ConcurrentHashMap<>();
    private final Map<Short, NotificationTypeCode> codeById = new ConcurrentHashMap<>();

    NotificationPersistenceAdapter(NotificationJpaRepository notifications, NotificationTypeJpaRepository types) {
        this.notifications = notifications;
        this.types = types;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = notification.notificationId() == null
                ? new NotificationJpaEntity(notification.userId(), typeId(notification.type()), notification.title(),
                        notification.message(), notification.referenceEntity(), notification.referenceId(),
                        notification.sentAt())
                : notifications.findById(notification.notificationId()).orElseThrow();
        entity.applyState(notification.readAt(), notification.deletedAt());
        return toDomain(notifications.save(entity));
    }

    @Override
    public Optional<Notification> findActive(long notificationId) {
        return notifications.findByIdAndDeletedAtIsNull(notificationId).map(this::toDomain);
    }

    @Override
    public PageResult<Notification> findActiveByUser(long userId, NotificationFilter filter, int page, int size) {
        Page<NotificationJpaEntity> result = notifications.findAll(byFilter(userId, filter),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("id"))));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public long countUnread(long userId) {
        return notifications.countByUserIdAndReadFalseAndDeletedAtIsNull(userId);
    }

    @Override
    public int markAllRead(long userId, Instant readAt) {
        return notifications.markAllRead(userId, readAt);
    }

    private Specification<NotificationJpaEntity> byFilter(long userId, NotificationFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (filter.read() != null) {
                predicates.add(cb.equal(root.get("read"), filter.read()));
            }
            if (filter.category() != null) {
                // la categoría no se guarda: se filtra por los tipos que pertenecen a ella
                List<Short> typeIds = Arrays.stream(NotificationTypeCode.values())
                        .filter(type -> type.category() == filter.category())
                        .map(this::typeId)
                        .toList();
                predicates.add(root.get("notificationTypeId").in(typeIds));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"),
                        filter.from().atStartOfDay(COLOMBIA).toInstant()));
            }
            if (filter.to() != null) {
                // "hasta" incluye todo ese día
                predicates.add(cb.lessThan(root.get("sentAt"),
                        filter.to().plusDays(1).atStartOfDay(COLOMBIA).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Notification toDomain(NotificationJpaEntity entity) {
        return Notification.restore(entity.getId(), entity.getUserId(), typeCode(entity.getNotificationTypeId()),
                entity.getTitle(), entity.getMessage(), entity.getReferenceEntity(), entity.getReferenceId(),
                entity.getSentAt(), entity.getReadAt(), entity.getDeletedAt());
    }

    private Short typeId(NotificationTypeCode code) {
        loadTypes();
        Short id = idByCode.get(code);
        if (id == null) {
            throw new UnknownNotificationTypeException(code.name());
        }
        return id;
    }

    private NotificationTypeCode typeCode(Short id) {
        loadTypes();
        NotificationTypeCode code = codeById.get(id);
        if (code == null) {
            throw new UnknownNotificationTypeException("id " + id);
        }
        return code;
    }

    // el catálogo lo carga el seed 108; si la base tiene un code que Java no conoce, se ignora
    private void loadTypes() {
        if (!idByCode.isEmpty()) {
            return;
        }
        Map<String, NotificationTypeCode> known = Arrays.stream(NotificationTypeCode.values())
                .collect(Collectors.toMap(Enum::name, type -> type));
        for (NotificationTypeJpaEntity type : types.findAll()) {
            NotificationTypeCode code = known.get(type.getCode());
            if (code != null) {
                codeById.put(type.getId(), code);
                if (type.isUsable()) {
                    idByCode.put(code, type.getId());
                }
            }
        }
    }
}
