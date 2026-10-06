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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@WebMvcTest(controllers={AuthController.class, OrderController.class})
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;
    
    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper; //Turns Java objects into JSON and vice versa

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp(){
        AuthenticationResponse ar = new AuthenticationResponse();
        ar.setToken("fdjskal");
        when(authService.register(any(RegisterRequest.class))).thenReturn(ar);
    }
    @Test
    void placingCorrectOrderTest() throws Exception { 
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
        // when(authService.register(any(RegisterRequest)))
       RegisterRequest registerRequest = new RegisterRequest();
       registerRequest.setUsername("a");
       registerRequest.setPassword("b");
       registerRequest.setEmail("fdsa@gmail.com");
       registerRequest.setFirstName("fdsa");
       registerRequest.setLastName("fsda");
       mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                        .andExpect(status().isCreated())
                        .andDo(print());
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