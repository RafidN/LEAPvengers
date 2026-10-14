package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.TickerNotFoundException;

import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.OrderRequest;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.TickerSearchResult;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;

import com.neueda.leap.security.JwtUtil;
import java.util.concurrent.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final LatestPriceQuotesRepository latestPriceQuotesRepository;
    private final HoldingsRepository holdingsRepository;
    private final AccountRepository accountRepository;
    private final TradingRules tradingRules;

    public OrderService(UserRepository userRepository, InstrumentsRepository instrumentsRepository,
                        LatestPriceQuotesRepository latestPriceQuotesRepository,
                        OrderRepository orderRepository, HoldingsRepository holdingsRepository,
                        AccountRepository accountRepository, TradingRules tradingRules) {
        this.userRepository = userRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.latestPriceQuotesRepository = latestPriceQuotesRepository;
        this.orderRepository = orderRepository;
        this.holdingsRepository = holdingsRepository;
        this.accountRepository = accountRepository;
        this.tradingRules = tradingRules;
    }


    @Transactional()
    public List<OrderHistoryResult> placeOrder(Integer userId, OrderRequest request) 
            throws UserNotFoundException, InvalidInputException, InvalidCredentialsException, TickerNotFoundException, InterruptedException {
        
        // Validate user exists and get their clientId
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate the shape of the request
        if (request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new InvalidInputException("Order ticker cannot be empty");
        }
        if (request.getQuantity().compareTo(new BigDecimal(0)) <= 0)
        {
            throw new InvalidInputException("Order quantity must be greater than 0");
        }
        String orderType = request.getOrderType() == null ? "" : request.getOrderType().trim().toUpperCase();
        if (!orderType.equals("BUY") && !orderType.equals("SELL")) {
            throw new InvalidInputException("Order type must be BUY or SELL");
        }
        int clientId = user.getClientId();
        String ticker = request.getTicker().trim();
        BigDecimal quantity = request.getQuantity();
        int accountId = request.getAccountId();
        List<AccountResponse> balances = accountRepository.findAccountResponsesByAccountId(accountId);
        if(balances.size() == 0) {
            throw new InvalidCredentialsException("Account not found");
        }
        // The account must belong to the caller; a missing account gets the same answer so ids can't be probed
        // TODO: IF SOMEONE WANTS TO IMPLEMENT THIS THEY CAN

        // System.out.println("BALANCE + " + balances.get(0).getBalance().toString());
        // int clientId2 = balances.get(0).getClientId();
        // if(clientId2 == user.getClientId()) {
        //     throw new ForbiddenException("User does not own the specified account");
        // }
        // Trading rules
        tradingRules.checkQuantity(quantity);
        Instruments instrument = instrumentsRepository.findByTickerIgnoreCase(ticker).orElse(null);
        tradingRules.checkInstrumentExists(instrument, ticker);
        LatestPriceQuotes quote = latestPriceQuotesRepository.findByInstrumentId(instrument.getInstrumentId()).orElse(null);
        tradingRules.checkPriceIsRecent(quote);
        BigDecimal price = quote.getPrice();
        BigDecimal balance = balances.get(0).getBalance();
        Holdings holding = holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrument.getInstrumentId())
                .orElse(null);
        if (orderType.equals("BUY")) {
            tradingRules.checkSufficientCash(balance, quantity, price);
        } else {
            tradingRules.checkSufficientHoldings(holding == null ? BigDecimal.ZERO : holding.getQuantity(), quantity);
        }
        BigDecimal cost = quantity.multiply(price);
        if(balance.compareTo(cost) < 0 && orderType.toUpperCase().equals("BUY")){
            throw new InvalidInputException("Cost too high for current balance");
        }
        List<TickerSearchResult> holdingInventory = holdingsRepository.searchByInstrumentQuery(clientId, ticker);
        Long tickerStock = 0l;
        for(TickerSearchResult tickerSearchResult : holdingInventory){
            if(tickerSearchResult.getAccountId() == accountId){
                tickerStock = tickerSearchResult.getQuantity();
            }
        }
        if(new BigDecimal(tickerStock).compareTo(quantity) < 0 && orderType.toUpperCase().equals("SELL")){
            throw new InvalidInputException("Not enough holding inventory in account to sell ");
        }


        Orders order = new Orders(accountId, instrument.getInstrumentId(), orderType, quantity, 
            price, LocalDate.now());
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        OrderDelay orderDelay = new OrderDelay(ticker, price, instrument, order);
        executorService.schedule(orderDelay, 5l, TimeUnit.SECONDS);

        List<OrderHistoryResult> result = new ArrayList<>();
        result.add(new OrderHistoryResult(order.getOrderId(), ticker, order.getOrderType(), order.getQuantity(), 
            order.getPrice(), "SUBMITTED", order.getOrderDate(), order.getSubmittedAt()));
        
        return result;
    }
    public class OrderDelay implements Runnable {
        public String ticker;
        public BigDecimal newPrice;
        public BigDecimal oldPrice;
        public Orders savedOrder;
        public Instruments instrument;
        // public List<PriceQuoteResult>
        public OrderDelay(String ticker, BigDecimal price, Instruments instrument, Orders savedOrder){
            this.ticker = ticker;
            this.oldPrice = price;
            this.savedOrder = savedOrder;
            this.instrument = instrument;
        }
        @Override
        public void run(){
            try{
            LatestPriceQuotes newQuote = latestPriceQuotesRepository.findByInstrumentId(instrument.getInstrumentId()).orElse(null);
            newPrice = newQuote.getPrice();
            if(oldPrice.compareTo(newPrice) == 0) {
                savedOrder.setOrderStatus("FILLED");
                savedOrder.setExecutionPrice(savedOrder.getPrice());
                System.out.println("FILLED");
            }
            else {
                savedOrder.setOrderStatus("REJECTED");
                savedOrder.setRejectionReason("Outdated order price from request");
                System.out.println("REJECTED");
            }
            orderRepository.save(savedOrder);
            }
            catch(Exception e)
            {
                System.out.println("WHATT");
                e.printStackTrace();
            }
        }
    }
}
