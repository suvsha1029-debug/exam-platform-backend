package com.shashwat.examplatformbackend.desktopconsole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesktopConsoleDeviceRepository extends JpaRepository<DesktopConsoleDevice, Long> {
    Optional<DesktopConsoleDevice> findByDeviceId(String deviceId);

    List<DesktopConsoleDevice> findAllByOrderByUpdatedAtDesc();
}
