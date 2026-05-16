package com.gserp.repository;

import com.gserp.model.Appointment;
import com.gserp.model.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Overlap query: appointment [startTime, endTime) intersects [start, end) for given staff,
     * excluding cancelled status. Used by SchedulerService for conflict detection.
     */
    @Query("""
           select a from Appointment a
           where a.staffId = :staffId
             and a.status <> :cancelled
             and a.startTime < :end
             and a.endTime > :start
           """)
    List<Appointment> findStaffOverlap(
            @Param("staffId") Long staffId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cancelled") AppointmentStatus cancelled);

    /**
     * Overlap query for a given resource id (joined via @ElementCollection).
     * Used by ResourceLockService.
     */
    @Query("""
           select a from Appointment a
              join a.resourceIds r
           where r = :resourceId
             and a.status <> :cancelled
             and a.startTime < :end
             and a.endTime > :start
           """)
    List<Appointment> findResourceOverlap(
            @Param("resourceId") Long resourceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cancelled") AppointmentStatus cancelled);

    List<Appointment> findByCustomerPhoneOrderByStartTimeDesc(String phone);
}
