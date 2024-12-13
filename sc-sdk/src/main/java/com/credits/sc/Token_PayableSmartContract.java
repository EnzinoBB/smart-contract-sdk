package com.credits.sc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.credits.scapi.v0.ExtensionStandard;
import com.credits.scapi.v3.*;

import static java.math.BigDecimal.ROUND_FLOOR;
import static java.math.BigDecimal.ZERO;

public class Token_PayableSmartContract extends SmartContract implements ExtensionStandard {

	private static String TOKEN_NAME = "";
	private static String TOKEN_SYMBOL = "";
	private static Long TOKEN_TOTAL_SUPPLY = 0L;
	private static int TOKEN_DECIMAL = 0;
	
    private final String owner;
    private final int decimal;
    HashMap<String, BigDecimal> balances;
    private String name;
    private String symbol;
    private BigDecimal totalCoins;
    private HashMap<String, Map<String, BigDecimal>> allowed;
    private boolean frozen;

    public Token_PayableSmartContract() {
        super();
        name = TOKEN_NAME;
        symbol = TOKEN_SYMBOL;
        decimal = TOKEN_DECIMAL;
        totalCoins = new BigDecimal(TOKEN_TOTAL_SUPPLY).setScale(decimal, ROUND_FLOOR);
        owner = initiator;
        allowed = new HashMap<>();
        balances = new HashMap<>();
        balances.put(owner, new BigDecimal(TOKEN_TOTAL_SUPPLY).setScale(decimal, ROUND_FLOOR));
    }

    @Override
    public int getDecimal() {
        return decimal;
    }

    @Override
    public void register() {
        balances.putIfAbsent(initiator, ZERO.setScale(decimal, ROUND_FLOOR));
    }

    @Override
    public boolean setFrozen(boolean isFrozen) {
        if (!initiator.equals(owner)) {
            throw new RuntimeException("unable change frozen state! The wallet "+ initiator + " is not owner");
        }
        this.frozen = isFrozen;
        return true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public String totalSupply() {
        return totalCoins.toString();
    }

    @Override
    public String balanceOf(String owner) {
        return getTokensBalance(owner).toString();
    }

    @Override
    public String allowance(String owner, String spender) {
        if (allowed.get(owner) == null) {
            return "0";
        }
        BigDecimal amount = allowed.get(owner).get(spender);
        return amount != null ? amount.toString() : "0";
    }

    @Override
    public boolean transfer(String to, String amount) {
        contractIsNotFrozen();
        if (!to.equals(initiator)) {
            BigDecimal decimalAmount = toBigDecimal(amount);
            amountIsValid(decimalAmount);
            BigDecimal sourceBalance = getTokensBalance(initiator);
            BigDecimal targetTokensBalance = getTokensBalance(to);
            if (decimalAmount.compareTo(ZERO.setScale(decimal, ROUND_FLOOR)) < 0) {
                throw new RuntimeException("invalid amount");
            }
            if(targetTokensBalance == null)
            {
                targetTokensBalance = new BigDecimal(0).setScale(decimal, ROUND_FLOOR);
            }
            if (sourceBalance.compareTo(decimalAmount) < 0) {
                throw new RuntimeException("the wallet"  + initiator + "doesn't have enough tokens to transfer");
            }
            balances.put(initiator, sourceBalance.subtract(decimalAmount));
            balances.put(to, targetTokensBalance.add(decimalAmount));
        }
        return true;
    }

    @Override
    public boolean transferFrom(String from, String to, String amount) {
        contractIsNotFrozen();

        if (!from.equals(to)) {
            BigDecimal sourceBalance = getTokensBalance(from);
            BigDecimal targetTokensBalance = getTokensBalance(to);
            if(targetTokensBalance == null)
            {
                targetTokensBalance = new BigDecimal(0).setScale(decimal, ROUND_FLOOR);
            }
            BigDecimal decimalAmount = toBigDecimal(amount);
            amountIsValid(decimalAmount);
            if (decimalAmount.compareTo(ZERO.setScale(decimal, ROUND_FLOOR)) < 0) {
                throw new RuntimeException("invalid amount");
            }
            if (sourceBalance.compareTo(decimalAmount) < 0)
                throw new RuntimeException("unable transfer tokens! The balance of " + from + " less then " + amount);

            Map<String, BigDecimal> spender = allowed.get(from);
            if (spender == null || !spender.containsKey(initiator))
                throw new RuntimeException("unable transfer tokens! The wallet " + from + " not allow transfer tokens for " + to);

            BigDecimal allowTokens = spender.get(initiator);
            if (allowTokens.compareTo(decimalAmount) < 0) {
                throw new RuntimeException("unable transfer tokens! Not enough allowed tokens. For the wallet " + initiator + " allow only " + allowTokens + " tokens");
            }

            spender.put(initiator, allowTokens.subtract(decimalAmount));
            balances.put(from, sourceBalance.subtract(decimalAmount));
            balances.put(to, targetTokensBalance.add(decimalAmount));
        }
        return true;
    }

    @Override
    public void approve(String spender, String amount) {
        initiatorIsRegistered();
        BigDecimal decimalAmount = toBigDecimal(amount);
        amountIsValid(decimalAmount);
        if (decimalAmount.compareTo(ZERO.setScale(decimal, ROUND_FLOOR)) < 0) {
            throw new RuntimeException("invalid amount");
        }
        Map<String, BigDecimal> initiatorSpenders = allowed.get(initiator);
        if (initiatorSpenders == null) {
            Map<String, BigDecimal> newSpender = new HashMap<>();
            newSpender.put(spender, decimalAmount);
            allowed.put(initiator, newSpender);
        } else {
            BigDecimal spenderAmount = initiatorSpenders.get(spender);
            initiatorSpenders.put(spender, spenderAmount == null ? decimalAmount : spenderAmount.add(decimalAmount));
        }
    }

    @Override
    public boolean burn(String amount) {
        contractIsNotFrozen();
        BigDecimal decimalAmount = toBigDecimal(amount);
        amountIsValid(decimalAmount);
        if (decimalAmount.compareTo(ZERO.setScale(decimal, ROUND_FLOOR)) < 0) {
            throw new RuntimeException("invalid amount");
        }
        if (!initiator.equals(owner))
            throw new RuntimeException("can not burn tokens! The wallet " + initiator + " is not owner");
        if (totalCoins.compareTo(decimalAmount) < 0) totalCoins = ZERO;
        else totalCoins = totalCoins.subtract(decimalAmount);
        return true;
    }

    @Override
    public boolean buyTokens(String amount) {
        return false;
    }

    private void contractIsNotFrozen() {
        if (frozen) throw new RuntimeException("unavailable action! The smart-contract is frozen");
    }

    private void amountIsValid(BigDecimal decimalAmount) {
        if (decimalAmount.compareTo(ZERO.setScale(decimal, ROUND_FLOOR)) < 0) {
            throw new RuntimeException("invalid amount");
        }
    }

    private void initiatorIsRegistered() {
        if (!balances.containsKey(initiator))
            throw new RuntimeException("unavailable action! The wallet " + initiator + " is not registered");
    }

    private BigDecimal toBigDecimal(String stringValue) {
        return new BigDecimal(stringValue).setScale(decimal, ROUND_FLOOR);
    }

    private BigDecimal getTokensBalance(String address) {
        return balances.get(address);
    }

    //ATTENTION: remove method (also if it causes compilation error) if a not
    //payable SmartContract has to be created
	@Override
	protected String payable(BigDecimal amount, byte[] userData) {
		contractIsNotFrozen();
		//Payable Logic in case: for example a donation to owner
		return null;
	}
    
}
