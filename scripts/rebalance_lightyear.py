#!/usr/bin/env python3
"""
Lightyear ETF Portfolio Rebalancer

Calculates buy/sell orders to equally rebalance 10 ETFs with additional cash.
"""

from dataclasses import dataclass
from decimal import Decimal, ROUND_DOWN, ROUND_HALF_UP
from typing import List


@dataclass
class Holding:
    symbol: str
    name: str
    price: Decimal
    quantity: Decimal

    @property
    def market_value(self) -> Decimal:
        return self.quantity * self.price


@dataclass
class Order:
    symbol: str
    name: str
    action: str
    quantity: Decimal
    price: Decimal
    value: Decimal


def calculate_rebalance(
    holdings: List[Holding],
    additional_cash: Decimal,
    num_etfs: int = 10
) -> tuple[List[Order], dict]:
    total_current_value = sum(h.market_value for h in holdings)
    total_portfolio_value = total_current_value + additional_cash
    target_per_etf = total_portfolio_value / num_etfs

    orders: List[Order] = []
    total_buy = Decimal("0")
    total_sell = Decimal("0")

    for holding in holdings:
        current_value = holding.market_value
        difference = target_per_etf - current_value

        if abs(difference) < Decimal("1"):
            continue

        if difference > 0:
            qty_to_buy = (difference / holding.price).quantize(Decimal("0.000001"), rounding=ROUND_DOWN)
            if qty_to_buy > 0:
                order_value = qty_to_buy * holding.price
                orders.append(Order(
                    symbol=holding.symbol,
                    name=holding.name,
                    action="BUY",
                    quantity=qty_to_buy,
                    price=holding.price,
                    value=order_value
                ))
                total_buy += order_value
        else:
            qty_to_sell = (abs(difference) / holding.price).quantize(Decimal("0.000001"), rounding=ROUND_DOWN)
            if qty_to_sell > 0:
                order_value = qty_to_sell * holding.price
                orders.append(Order(
                    symbol=holding.symbol,
                    name=holding.name,
                    action="SELL",
                    quantity=qty_to_sell,
                    price=holding.price,
                    value=order_value
                ))
                total_sell += order_value

    summary = {
        "total_current_value": total_current_value,
        "additional_cash": additional_cash,
        "total_portfolio_value": total_portfolio_value,
        "target_per_etf": target_per_etf,
        "total_buy": total_buy,
        "total_sell": total_sell,
        "net_cash_needed": total_buy - total_sell
    }

    return orders, summary


def main():
    holdings = [
        Holding("CSX5:AEX:EUR", "iShares Core EURO STOXX 50", Decimal("221.23"), Decimal("28.266904608")),
        Holding("EXUS:GER:EUR", "Xtrackers MSCI World Ex USA", Decimal("35.20"), Decimal("176.732834553")),
        Holding("QDVE:GER:EUR", "iShares S&P 500 IT Sector", Decimal("35.93"), Decimal("172.542736442")),
        Holding("VNRA:GER:EUR", "Vanguard FTSE North America", Decimal("147.32"), Decimal("41.987930805")),
        Holding("WBIT:GER:EUR", "WisdomTree Physical Bitcoin", Decimal("17.88"), Decimal("267.912301452")),
        Holding("WTAI:MIL:EUR", "WisdomTree Artificial Intelligence", Decimal("73.72"), Decimal("84.445848826")),
        Holding("XAIX:GER:EUR", "Xtrackers AI & Big Data", Decimal("155.53"), Decimal("39.930732443")),
        Holding("XNAS:GER:EUR", "Xtrackers Nasdaq 100", Decimal("50.23"), Decimal("123.171269167")),
        Holding("DFEN:GER:EUR", "VanEck Defense", Decimal("52.27"), Decimal("0")),
        Holding("DFND:PAR:EUR", "iShares Global Aerospace & Defence", Decimal("7.78"), Decimal("0")),
    ]

    additional_cash = Decimal("1000")

    orders, summary = calculate_rebalance(holdings, additional_cash)

    print("=" * 80)
    print("LIGHTYEAR ETF PORTFOLIO REBALANCER")
    print("=" * 80)
    print()
    print("CURRENT HOLDINGS:")
    print("-" * 80)
    print(f"{'Symbol':<15} {'Name':<35} {'Qty':>12} {'Price':>10} {'Value':>12}")
    print("-" * 80)

    for h in holdings:
        print(f"{h.symbol:<15} {h.name[:35]:<35} {h.quantity:>12.6f} {h.price:>10.2f} {h.market_value:>12.2f}")

    print("-" * 80)
    print(f"{'Total Current Value:':<64} {summary['total_current_value']:>12.2f}")
    print(f"{'Additional Cash:':<64} {summary['additional_cash']:>12.2f}")
    print(f"{'Total Portfolio Value:':<64} {summary['total_portfolio_value']:>12.2f}")
    print(f"{'Target per ETF (10 equal):':<64} {summary['target_per_etf']:>12.2f}")
    print()

    print("REBALANCING ORDERS:")
    print("-" * 80)
    print(f"{'Action':<6} {'Symbol':<15} {'Name':<30} {'Qty':>12} {'Price':>10} {'Value':>10}")
    print("-" * 80)

    sell_orders = [o for o in orders if o.action == "SELL"]
    buy_orders = [o for o in orders if o.action == "BUY"]

    for order in sell_orders:
        print(f"\033[91m{order.action:<6}\033[0m {order.symbol:<15} {order.name[:30]:<30} {order.quantity:>12.6f} {order.price:>10.2f} {order.value:>10.2f}")

    for order in buy_orders:
        print(f"\033[92m{order.action:<6}\033[0m {order.symbol:<15} {order.name[:30]:<30} {order.quantity:>12.6f} {order.price:>10.2f} {order.value:>10.2f}")

    print("-" * 80)
    print()
    print("SUMMARY:")
    print(f"  Total to SELL: €{summary['total_sell']:,.2f}")
    print(f"  Total to BUY:  €{summary['total_buy']:,.2f}")
    print(f"  Net cash needed: €{summary['net_cash_needed']:,.2f}")
    print(f"  Available cash:  €{summary['additional_cash']:,.2f}")

    cash_remaining = summary['additional_cash'] - summary['net_cash_needed']
    print(f"  Cash remaining:  €{cash_remaining:,.2f}")
    print()

    print("POST-REBALANCE PORTFOLIO:")
    print("-" * 80)
    print(f"{'Symbol':<15} {'Current':>12} {'Target':>12} {'New Qty':>12} {'New Value':>12}")
    print("-" * 80)

    order_map = {o.symbol: o for o in orders}
    for h in holdings:
        order = order_map.get(h.symbol)
        new_qty = h.quantity
        if order:
            if order.action == "BUY":
                new_qty += order.quantity
            else:
                new_qty -= order.quantity
        new_value = new_qty * h.price
        print(f"{h.symbol:<15} {h.market_value:>12.2f} {summary['target_per_etf']:>12.2f} {new_qty:>12.6f} {new_value:>12.2f}")

    print("-" * 80)

    print()
    print("=" * 80)
    print("SQL TRANSACTIONS (for database):")
    print("=" * 80)
    print()

    for order in sell_orders + buy_orders:
        tx_type = order.action
        print(f"-- {tx_type} {order.symbol}: {order.quantity:.6f} @ €{order.price:.2f} = €{order.value:.2f}")

    print()


if __name__ == "__main__":
    main()
