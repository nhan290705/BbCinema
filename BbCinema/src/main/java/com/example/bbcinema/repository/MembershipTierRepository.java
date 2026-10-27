package com.example.bbcinema.repository;

import com.example.bbcinema.model.user.MembershipTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipTierRepository
        extends JpaRepository<MembershipTier, Integer> {

    @Override
    Optional<MembershipTier> findById(Integer id);
}
