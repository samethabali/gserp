package com.gscrm.service;

import com.gscrm.model.ServiceDefinition;
import com.gscrm.model.enums.ServiceCategory;
import com.gscrm.repository.ServiceDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceTemplateServiceTest {

    @Mock
    private ServiceDefinitionRepository serviceDefinitionRepository;

    @InjectMocks
    private ServiceTemplateService serviceTemplateService;

    @Test
    void seedHairAndSkinMenu_createsFiveServicesWhenEmpty() {
        when(serviceDefinitionRepository.existsBySalonId(5L)).thenReturn(false);
        when(serviceDefinitionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<ServiceDefinition> result = serviceTemplateService.seedHairAndSkinMenu(5L);

        assertThat(result).hasSize(5);
        assertThat(result).extracting(ServiceDefinition::getCategory)
                .contains(ServiceCategory.HAIR, ServiceCategory.SKIN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServiceDefinition>> captor = ArgumentCaptor.forClass(List.class);
        verify(serviceDefinitionRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allMatch(s -> s.getSalonId().equals(5L));
    }

    @Test
    void seedHairAndSkinMenu_skipsWhenSalonAlreadyHasServices() {
        when(serviceDefinitionRepository.existsBySalonId(5L)).thenReturn(true);
        when(serviceDefinitionRepository.findBySalonIdAndActiveTrue(5L))
                .thenReturn(List.of(ServiceDefinition.builder().salonId(5L).name("Mevcut").build()));

        List<ServiceDefinition> result = serviceTemplateService.seedHairAndSkinMenu(5L);

        assertThat(result).hasSize(1);
        verify(serviceDefinitionRepository, never()).saveAll(anyList());
    }
}
