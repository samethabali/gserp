package com.gscrm.security;

import com.gscrm.model.User;
import com.gscrm.repository.UserRepository;
import com.gscrm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Kullanıcıyı adıyla bulur.
     *
     * <p>Personel kullanıcı adları V33'ten beri sistem genelinde tekil; giriş bu yüzden
     * kiracı bilmeden çözülebiliyor ve giriş sayfası artık hangi işletmeye ait olduğunu
     * adresten öğrenmek zorunda değil. Müşteri portalı kullanıcıları e-posta ile kayıt
     * olduğu ve aynı e-posta iki farklı işletmede bulunabileceği için salon bazlı kalır:
     * kiracı bağlamı varsa önce o salonda aranır.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Long salonId = TenantContext.getSalonId();
        User user = null;
        if (salonId != null) {
            user = userRepository.findBySalonIdAndUsername(salonId, username).orElse(null);
        }
        if (user == null) {
            user = userRepository.findStaffByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + username));
        }
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled(),
                user.getRole(),
                user.getStaffId(),
                user.getCustomerId(),
                user.getSalonId(),
                user.getOrganizationId(),
                user.isMustChangePassword(),
                user.getTokenVersion(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
