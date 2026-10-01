package com.reservasalas.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reservasalas.dto.RoomDto;
import com.reservasalas.service.RoomService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RoomService service;

    @Test
    void listaSalasConCamposDelContrato() throws Exception {
        when(service.findAll()).thenReturn(List.of(new RoomDto(1L, "Sala A", 8, 2)));

        mvc.perform(get("/api/salas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Sala A"))
                .andExpect(jsonPath("$[0].capacidad").value(8))
                .andExpect(jsonPath("$[0].planta").value(2));
    }
}
