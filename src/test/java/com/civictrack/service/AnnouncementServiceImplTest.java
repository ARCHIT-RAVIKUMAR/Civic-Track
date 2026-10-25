package com.civictrack.service;

import com.civictrack.dto.AnnouncementForm;
import com.civictrack.entity.Announcement;
import com.civictrack.entity.User;
import com.civictrack.enums.AnnouncementSeverity;
import com.civictrack.enums.Role;
import com.civictrack.exception.BusinessRuleException;
import com.civictrack.exception.ForbiddenOperationException;
import com.civictrack.repository.AnnouncementRepository;
import com.civictrack.service.impl.AnnouncementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceImplTest {

    @Mock private AnnouncementRepository announcementRepository;

    private AnnouncementServiceImpl service;
    private User admin;
    private User citizen;

    @BeforeEach
    void setUp() {
        service = new AnnouncementServiceImpl(announcementRepository);
        admin = new User("Admin", "admin@example.com", "hash", Role.ADMIN);
        citizen = new User("Citizen", "citizen@example.com", "hash", Role.CITIZEN);
    }

    private AnnouncementForm form(LocalDate expiresOn) {
        AnnouncementForm f = new AnnouncementForm();
        f.setTitle("Water cut");
        f.setMessage("Tomorrow 9 to 5");
        f.setSeverity(AnnouncementSeverity.WARNING);
        f.setExpiresOn(expiresOn);
        return f;
    }

    @Test
    void adminCanPublishAnAnnouncementThatLastsUntilTheEndOfTheDay() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));

        Announcement created = service.create(form(LocalDate.now()), admin);

        assertEquals("Water cut", created.getTitle());
        assertNotNull(created.getExpiresAt());
        assertFalse(created.isExpired());   // still visible today
        assertTrue(created.isActive());
    }

    @Test
    void endDateInThePastIsRejected() {
        assertThrows(BusinessRuleException.class, () -> service.create(form(LocalDate.now().minusDays(1)), admin));
        verify(announcementRepository, never()).save(any(Announcement.class));
    }

    @Test
    void nonAdminsCannotManageAnnouncements() {
        assertThrows(ForbiddenOperationException.class, () -> service.create(form(null), citizen));
        assertThrows(ForbiddenOperationException.class, () -> service.findAll(citizen));
        assertThrows(ForbiddenOperationException.class, () -> service.toggle(UUID.randomUUID(), citizen));
        assertThrows(ForbiddenOperationException.class, () -> service.delete(UUID.randomUUID(), citizen));
    }

    @Test
    void toggleSwitchesAnAnnouncementOffAndOn() {
        Announcement announcement = new Announcement("T", "M", AnnouncementSeverity.INFO, null, admin);
        UUID id = UUID.randomUUID();
        when(announcementRepository.findById(id)).thenReturn(Optional.of(announcement));

        service.toggle(id, admin);
        assertFalse(announcement.isActive());
        service.toggle(id, admin);
        assertTrue(announcement.isActive());
        verify(announcementRepository, org.mockito.Mockito.times(2)).save(announcement);
    }

    @Test
    void savedAnnouncementKeepsTheAdminAsAuthor() {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(form(null), admin);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        assertEquals(admin, captor.getValue().getCreatedBy());
    }
}
