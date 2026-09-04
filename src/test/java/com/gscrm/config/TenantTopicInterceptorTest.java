package com.gscrm.config;

import com.gscrm.model.enums.UserRole;
import com.gscrm.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STOMP kanalının kiracı sınırı.
 *
 * <p>SUBSCRIBE hiç denetlenmiyordu: adresi istemci seçtiği için kimliği doğrulanmış
 * herhangi bir kullanıcı başka bir işletmenin randevu bildirimlerini dinleyebiliyordu.
 */
@DisplayName("WebSocket kiracı sınırı")
class TenantTopicInterceptorTest {

    private final TenantTopicInterceptor interceptor = new TenantTopicInterceptor();

    @Test
    @DisplayName("kendi salonunun kanalına abone olunabilir")
    void allowsOwnSalonTopic() {
        assertThatCode(() -> subscribe("/topic/salon.7.appointments", principal(7L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("başka salonun kanalı reddedilir")
    void rejectsForeignSalonTopic() {
        assertThatThrownBy(() -> subscribe("/topic/salon.1.appointments", principal(7L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yetkiniz yok");
    }

    @Test
    @DisplayName("salon kapsamı olmayan topic reddedilir")
    void rejectsUnscopedTopic() {
        assertThatThrownBy(() -> subscribe("/topic/appointments", principal(7L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("salonsuz kullanıcı hiçbir salon kanalına abone olamaz")
    void rejectsWhenSessionHasNoSalon() {
        assertThatThrownBy(() -> subscribe("/topic/salon.1.appointments", principal(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("dashboard ve notifications kanalları da aynı kurala tabi")
    void appliesToEverySalonScopedTopic() {
        assertThatCode(() -> subscribe("/topic/salon.7.dashboard", principal(7L)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> subscribe("/topic/salon.8.notifications", principal(7L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CONNECT hâlâ kimlik ister")
    void connectStillRequiresAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kimlik doğrulama");
    }

    /** Uygulama hedefleri kural dışı: kiracı denetimi controller tarafında. */
    @Test
    @DisplayName("/app hedefleri etkilenmez")
    void leavesApplicationDestinationsAlone() {
        assertThatCode(() -> subscribe("/app/appointment/move", principal(7L)))
                .doesNotThrowAnyException();
    }

    private void subscribe(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(user);
        interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), null);
    }

    private Authentication principal(Long salonId) {
        AuthenticatedUser user = new AuthenticatedUser(
                1L, "demo", "hash", true, UserRole.BRANCH_MANAGER,
                null, null, salonId, 1L, false, 0,
                List.of(new SimpleGrantedAuthority("ROLE_BRANCH_MANAGER")));
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
