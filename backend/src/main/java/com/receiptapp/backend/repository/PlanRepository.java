package com.receiptapp.backend.repository;

import com.receiptapp.backend.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, String> {
}