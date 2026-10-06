package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.LoginRequest;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.AuthService;
import com.neueda.leap.service.OrderService;
import com.neueda.leap.controller.AuthController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers={AuthController.class, OrderController.class})
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AuthService authService;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper; //Turns Java objects into JSON and vice versa

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp(){
    }
    @Test
    void placingCorrectOrderTest() throws Exception { 
        when(authService.register(any(RegisterRequest.class))).thenReturn(new AuthenticationResponse(...));
        // orderService.orderHistoryResult = new OrderHistoryResult(
        //         67,
        //         "NFLX",
        //         "BUY",
        //         67,
        //         67,
        //         "ACCEPTED",
        //         "2026-10-01",
        //         "2026-10-01T11:53:28.5576925"
        // );
        MvcResult mvcResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "firstName":"fjekl",
                                    "lastName":"jefklfe",
                                    "email":"jfkelajfkle@gmail.com",
                                    "username":"feafd",
                                    "password":"fdsjaklfdsal"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-register-token"))
                .andReturn();
        // String resultString = mvcResult.getResponse().getContentAsString();
        // System.out.println("THE RESULT\n" + resultString);
        // mockMvc.perform(post("/order/placed")
        //                 .contentType(MediaType.APPLICATION_JSON)
        //                 .header("Authorization", "BEARER JKLFD")
        //                 .content("""
        //                         {
        //                             "ticker":"NFLX",
        //                             "quantity":7,
        //                             "orderType":"BUY",
        //                             "accountId":5
        //                         }
        //                         """))
        //         .andExpect(status().isCreated())
        //         .andReturn();

    }
}