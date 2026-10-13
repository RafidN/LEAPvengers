package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.OrderRequest;

import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @Transactional
    public List<OrderHistoryResult> placeOrder(Integer userId, OrderRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate the shape of the request
        if (request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new InvalidInputException("Order ticker cannot be empty");
        }
        if (request.getAccountId() == null) {
            throw new InvalidInputException("Order account cannot be empty");
        }
        String orderType = request.getOrderType() == null ? "" : request.getOrderType().trim().toUpperCase();
        if (!orderType.equals("BUY") && !orderType.equals("SELL")) {
            throw new InvalidInputException("Order type must be BUY or SELL");
        }
        String ticker = request.getTicker().trim();
        BigDecimal quantity = request.getQuantity();

        // The account must belong to the caller; a missing account gets the same answer so ids can't be probed
        Accounts account = accountRepository.findById(request.getAccountId())
                .filter(a -> a.getClientId().equals(user.getClientId()))
                .orElseThrow(() -> new ForbiddenException("User does not own the specified account"));

        // Trading rules
        tradingRules.checkQuantity(quantity);
        Instruments instrument = instrumentsRepository.findByTickerIgnoreCase(ticker).orElse(null);
        tradingRules.checkInstrumentExists(instrument, ticker);
        LatestPriceQuotes quote = latestPriceQuotesRepository.findByInstrumentId(instrument.getInstrumentId()).orElse(null);
        tradingRules.checkPriceIsRecent(quote);
        BigDecimal price = quote.getPrice();
        Holdings holding = holdingsRepository.findByAccountIdAndInstrumentId(account.getAccountId(), instrument.getInstrumentId())
                .orElse(null);
        if (orderType.equals("BUY")) {
            tradingRules.checkSufficientCash(account.getBalance(), quantity, price);
        } else {
            tradingRules.checkSufficientHoldings(holding == null ? BigDecimal.ZERO : holding.getQuantity(), quantity);
        }

        // Fill at the checked price. Cash, holdings and the order all change in this one transaction,
        // so a failure part way through leaves none of them changed.
        BigDecimal amount = quantity.multiply(price);
        account.setBalance(orderType.equals("BUY") ? account.getBalance().subtract(amount) : account.getBalance().add(amount));
        accountRepository.save(account);
        updateHolding(holding, account.getAccountId(), instrument.getInstrumentId(),
                orderType.equals("BUY") ? quantity : quantity.negate());

        Orders order = new Orders(account.getAccountId(), instrument.getInstrumentId(), orderType, quantity, price, LocalDate.now());
        order.setOrderStatus("FILLED");
        order.setExecutionPrice(price);
        order.setExecutedAt(LocalDateTime.now());
        Orders savedOrder = orderRepository.save(order);

        return List.of(new OrderHistoryResult(savedOrder.getOrderId(), instrument.getTicker(), savedOrder.getOrderType(),
                savedOrder.getQuantity(), savedOrder.getPrice(), savedOrder.getOrderStatus(), savedOrder.getOrderDate(),
                savedOrder.getSubmittedAt()));
    }

    /**
     * Applies a filled order to the account's position: a buy adds to it (opening one if needed),
     * a sell takes away from it, and a position sold down to zero is removed.
     */
    private void updateHolding(Holdings holding, Integer accountId, Integer instrumentId, BigDecimal change) {
        if (holding == null) {
            holdingsRepository.save(new Holdings(accountId, instrumentId, change, LocalDate.now()));
            return;
        }
        BigDecimal newQuantity = holding.getQuantity().add(change);
        if (newQuantity.signum() == 0) {
            holdingsRepository.delete(holding);
            return;
        }
        holding.setQuantity(newQuantity);
        holding.setAsOfDate(LocalDate.now());
        holdingsRepository.save(holding);
    }
}
