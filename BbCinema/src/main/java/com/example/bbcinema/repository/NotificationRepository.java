package com.example.bbcinema.repository;

import com.example.bbcinema.model.user.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    @Override
    Optional<Notification> findById(Long id);
}
