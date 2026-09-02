package com.gscrm.repository;

import com.gscrm.model.Appointment;
import com.gscrm.model.enums.AppointmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndSalonId(Long id, Long salonId);

    List<Appointment> findBySalonId(Long salonId);

    List<Appointment> findBySalonIdOrderByStartTimeDesc(Long salonId, Pageable pageable);

    List<Appointment> findBySalonIdAndStartTimeBetween(Long salonId, LocalDateTime start, LocalDateTime end);

    List<Appointment> findBySalonIdAndStaffIdAndStartTimeBetween(Long salonId, Long staffId, LocalDateTime start, LocalDateTime end);

    List<Appointment> findBySalonIdAndStartTimeBetweenAndStatusIn(
            Long salonId, LocalDateTime start, LocalDateTime end, List<AppointmentStatus> statuses);

    @Query("""
           select a from Appointment a
           where a.salonId = :salonId
             and a.staffId = :staffId
             and a.status <> :cancelled
             and a.startTime < :end
             and a.endTime > :start
           """)
    List<Appointment> findStaffOverlap(
            @Param("salonId") Long salonId,
            @Param("staffId") Long staffId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cancelled") AppointmentStatus cancelled);

    @Query("""
           select a from Appointment a
              join a.resourceIds r
           where a.salonId = :salonId
             and r = :resourceId
             and a.status <> :cancelled
             and a.startTime < :end
             and a.endTime > :start
           """)
    List<Appointment> findResourceOverlap(
            @Param("salonId") Long salonId,
            @Param("resourceId") Long resourceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("cancelled") AppointmentStatus cancelled);

    List<Appointment> findBySalonIdAndCustomerPhoneOrderByStartTimeDesc(Long salonId, String phone);

    long countBySalonIdAndCustomerPhone(Long salonId, String phone);

    long countBySalonIdAndCustomerPhoneAndStartTimeAfter(Long salonId, String phone, LocalDateTime after);

    List<Appointment> findBySalonIdAndCustomerPhoneAndStartTimeBeforeOrderByStartTimeDesc(
            Long salonId, String phone, LocalDateTime before);

    List<Appointment> findBySalonIdAndCustomerPhoneAndStartTimeAfterOrderByStartTimeAsc(
            Long salonId, String phone, LocalDateTime after);

    @Query("""
            select a from Appointment a
            where a.salonId = :salonId
              and a.sessionGroupId is not null
              and a.status = 'SCHEDULED'
              and a.startTime >= :start
              and a.startTime < :end
            """)
    List<Appointment> findUpcomingSessionAppointments(
            @Param("salonId") Long salonId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select a from Appointment a
            where a.salonId = :salonId
              and a.customerPhone = :phone
              and a.status in :statuses
            order by a.startTime asc
            """)
    List<Appointment> findBySalonIdAndCustomerPhoneAndStatusIn(
            @Param("salonId") Long salonId,
            @Param("phone") String phone,
            @Param("statuses") List<AppointmentStatus> statuses);

    List<Appointment> findBySalonIdAndCustomerPhoneOrderByStartTimeAsc(Long salonId, String phone);

    long countBySalonIdAndCustomerPhoneAndStatus(Long salonId, String phone, AppointmentStatus status);
}
