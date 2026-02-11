#!/usr/bin/env python3
"""
Defense ETF Rebalancing Calculator

Calculates transactions needed to rebalance DFND, DFEN, and EUDF to 33.33% each on Lightyear.
"""

from dataclasses import dataclass
from decimal import Decimal, ROUND_DOWN
from datetime import date


@dataclass
class Holding:
    symbol: str
    quantity: Decimal
    current_price: Decimal

    @property
    def value(self) -> Decimal:
        return self.quantity * self.current_price


@dataclass
class Transaction:
    symbol: str
    transaction_type: str
    quantity: Decimal
    price: Decimal
    transaction_date: date
    platform: str = "LIGHTYEAR"

    @property
    def value(self) -> Decimal:
        return self.quantity * self.price


def calculate_rebalancing(holdings: list[Holding], target_weight: Decimal = Decimal("0.333333333")) -> list[Transaction]:
    total_value = sum(h.value for h in holdings)
    print(f"Total portfolio value: €{total_value:.2f}")
    print()

    target_value_per_etf = total_value * target_weight
    print(f"Target value per ETF (33.33%): €{target_value_per_etf:.2f}")
    print()

    transactions = []
    today = date.today()

    for holding in holdings:
        current_value = holding.value
        diff_value = target_value_per_etf - current_value
        diff_quantity = (diff_value / holding.current_price).quantize(Decimal("0.000000001"), rounding=ROUND_DOWN)

        print(f"{holding.symbol}:")
        print(f"  Current: {holding.quantity:.9f} units @ €{holding.current_price:.4f} = €{current_value:.2f}")
        print(f"  Target value: €{target_value_per_etf:.2f}")
        print(f"  Difference: €{diff_value:.2f} ({diff_quantity:+.9f} units)")

        if diff_quantity > 0:
            transactions.append(Transaction(
                symbol=holding.symbol,
                transaction_type="BUY",
                quantity=diff_quantity,
                price=holding.current_price,
                transaction_date=today
            ))
            print(f"  Action: BUY {diff_quantity:.9f} units")
        elif diff_quantity < 0:
            transactions.append(Transaction(
                symbol=holding.symbol,
                transaction_type="SELL",
                quantity=abs(diff_quantity),
                price=holding.current_price,
                transaction_date=today
            ))
            print(f"  Action: SELL {abs(diff_quantity):.9f} units")
        else:
            print(f"  Action: No change needed")
        print()

    return transactions


def generate_sql(transactions: list[Transaction]) -> str:
    if not transactions:
        return "-- No transactions needed"

    lines = [
        "INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform,",
        "                                   commission)",
        "VALUES"
    ]

    tx_lines = []
    for tx in transactions:
        tx_lines.append(
            f"       ((SELECT id FROM instrument WHERE symbol = '{tx.symbol}'), "
            f"'{tx.transaction_type}', {tx.quantity}, {tx.price},\n"
            f"        '{tx.transaction_date}', '{tx.platform}', 0)"
        )

    lines.append(",\n".join(tx_lines) + ";")
    return "\n".join(lines)


def main():
    print("=" * 70)
    print("Defense ETF Rebalancing Calculator")
    print("Target: DFND, DFEN, EUDF each at 33.33% on Lightyear")
    print("=" * 70)
    print()

    dfnd_quantity = Decimal("631.827674241") - Decimal("80.986348542")
    dfen_quantity = Decimal("94.180246913") - Decimal("13.49383378")
    eudf_quantity = Decimal("0")

    dfnd_price = Decimal("8.539")
    dfen_price = Decimal("61.790")
    eudf_price = Decimal("34.198")

    print("Current Lightyear Holdings (from migrations):")
    print(f"  DFND: 631.827674241 - 80.986348542 = {dfnd_quantity}")
    print(f"  DFEN: 94.180246913 - 13.49383378 = {dfen_quantity}")
    print(f"  EUDF: {eudf_quantity} (new instrument)")
    print()

    print("Current Prices (from API):")
    print(f"  DFND:PAR:EUR: €{dfnd_price}")
    print(f"  DFEN:GER:EUR: €{dfen_price}")
    print(f"  EUDF:GER:EUR: €{eudf_price}")
    print()

    holdings = [
        Holding("DFND:PAR:EUR", dfnd_quantity, dfnd_price),
        Holding("DFEN:GER:EUR", dfen_quantity, dfen_price),
        Holding("EUDF:GER:EUR", eudf_quantity, eudf_price),
    ]

    print("=" * 70)
    print("Calculating Rebalancing...")
    print("=" * 70)
    print()

    transactions = calculate_rebalancing(holdings)

    sells = [t for t in transactions if t.transaction_type == "SELL"]
    buys = [t for t in transactions if t.transaction_type == "BUY"]

    sell_value = sum(t.value for t in sells)
    buy_value = sum(t.value for t in buys)

    print("=" * 70)
    print("Summary")
    print("=" * 70)
    print(f"Total to sell: €{sell_value:.2f}")
    print(f"Total to buy: €{buy_value:.2f}")
    print(f"Net difference: €{abs(buy_value - sell_value):.2f}")
    print()

    print("=" * 70)
    print("Verification (new allocations)")
    print("=" * 70)
    total_value = sum(h.value for h in holdings)
    for h in holdings:
        tx = next((t for t in transactions if t.symbol == h.symbol), None)
        if tx:
            if tx.transaction_type == "BUY":
                new_qty = h.quantity + tx.quantity
            else:
                new_qty = h.quantity - tx.quantity
        else:
            new_qty = h.quantity
        new_value = new_qty * h.current_price
        pct = (new_value / total_value * 100) if total_value > 0 else 0
        print(f"  {h.symbol}: {new_qty:.9f} units = €{new_value:.2f} ({pct:.2f}%)")
    print()

    print("=" * 70)
    print("SQL Migration")
    print("=" * 70)
    print()
    sql = generate_sql(transactions)
    print(sql)


if __name__ == "__main__":
    main()
