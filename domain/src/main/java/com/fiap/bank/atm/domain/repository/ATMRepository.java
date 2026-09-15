package com.fiap.bank.atm.domain.repository;

import com.fiap.bank.atm.domain.model.BaseEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ATMRepository<T extends BaseEntity> {
    Optional<T> findById(UUID id);

    List<T> findAll();

    void save(T entity);
}
