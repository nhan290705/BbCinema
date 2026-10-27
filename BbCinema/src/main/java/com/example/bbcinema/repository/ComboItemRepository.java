package com.example.bbcinema.repository;

import com.example.bbcinema.model.fnb.ComboItem;
import com.example.bbcinema.model.fnb.ComboItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ComboItemRepository
        extends JpaRepository<ComboItem, ComboItemId> {

    @Override
    Optional<ComboItem> findById(ComboItemId id);
}