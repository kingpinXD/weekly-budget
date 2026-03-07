#!/usr/bin/env python3
"""
Firebase RTDB utility for Weekly Totals app.
Usage:
    # Add a transaction to a specific week
    python scripts/firebase_tool.py add-txn --week 2026-02-28 --category GROCERIES --amount 101 --details "costco"

    # List transactions for a week
    python scripts/firebase_tool.py list-txn --week 2026-02-28

    # Delete a transaction by its createdAt key
    python scripts/firebase_tool.py del-txn --key 1772339400000

    # View savings for a week
    python scripts/firebase_tool.py get-savings --week 2026-02-28

    # Set savings for a week
    python scripts/firebase_tool.py set-savings --week 2026-02-28 --amount 429.53

    # List all split entries
    python scripts/firebase_tool.py list-split

    # Add a split entry
    python scripts/firebase_tool.py add-split --category CREDIT_CARD --amount 50 --comment "dinner" --type EQUAL --email tanmay.bhattacharya.smit@gmail.com

    # View full database tree (top-level keys)
    python scripts/firebase_tool.py tree
"""

import argparse
import datetime
import json
import os
import sys

import firebase_admin
from firebase_admin import credentials, db

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
SERVICE_ACCOUNT = os.path.join(PROJECT_ROOT, "weekly-totals-firebase-adminsdk-fbsvc-920b6d2091.json")
DATABASE_URL = "https://weekly-totals-default-rtdb.firebaseio.com"


def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate(SERVICE_ACCOUNT)
        firebase_admin.initialize_app(cred, {"databaseURL": DATABASE_URL})


def cmd_add_txn(args):
    init_firebase()
    created_at = args.key or int(datetime.datetime.now().timestamp() * 1000)
    ref = db.reference(f"weekly_totals/transactions/{created_at}")
    data = {
        "weekStartDate": args.week,
        "category": args.category.upper(),
        "amount": args.amount,
        "isAdjustment": args.adjustment,
        "createdAt": created_at,
    }
    if args.details:
        data["details"] = args.details
    ref.set(data)
    print(f"Added: {data['category']} ${args.amount} -> key {created_at}")

    if args.update_savings:
        update_savings_for_week(args.week, -args.amount)


def cmd_list_txn(args):
    init_firebase()
    all_txns = db.reference("weekly_totals/transactions").get() or {}
    filtered = {k: v for k, v in all_txns.items() if v.get("weekStartDate") == args.week}

    if not filtered:
        print(f"No transactions for week {args.week}")
        return

    total = 0
    for key in sorted(filtered.keys()):
        t = filtered[key]
        amt = t.get("amount", 0)
        total += amt
        details = f' ({t["details"]})' if t.get("details") else ""
        adj = " [ADJ]" if t.get("isAdjustment") else ""
        print(f"  {key}  {t.get('category', '?'):15s}  ${amt:>8.2f}{adj}{details}")

    print(f"\n  Total: ${total:.2f}")


def cmd_del_txn(args):
    init_firebase()
    ref = db.reference(f"weekly_totals/transactions/{args.key}")
    existing = ref.get()
    if existing is None:
        print(f"No transaction with key {args.key}")
        return
    ref.delete()
    print(f"Deleted: {existing.get('category')} ${existing.get('amount')} (key {args.key})")

    if args.update_savings and existing.get("weekStartDate"):
        update_savings_for_week(existing["weekStartDate"], existing.get("amount", 0))


def cmd_get_savings(args):
    init_firebase()
    if args.week:
        val = db.reference(f"weekly_totals/savings/{args.week}").get()
        print(f"Savings for {args.week}: ${val}" if val is not None else f"No savings record for {args.week}")
    else:
        all_savings = db.reference("weekly_totals/savings").get() or {}
        total = 0
        for week in sorted(all_savings.keys()):
            amt = all_savings[week]
            total += amt if isinstance(amt, (int, float)) else 0
            print(f"  {week}: ${amt:.2f}" if isinstance(amt, (int, float)) else f"  {week}: {amt}")
        print(f"\n  Total savings: ${total:.2f}")


def cmd_set_savings(args):
    init_firebase()
    ref = db.reference(f"weekly_totals/savings/{args.week}")
    old = ref.get()
    ref.set(args.amount)
    print(f"Savings for {args.week}: ${old} -> ${args.amount}")


def cmd_list_split(args):
    init_firebase()
    entries = db.reference("weekly_totals/split/entries").get() or {}
    if not entries:
        print("No split entries")
        return

    for key in sorted(entries.keys()):
        e = entries[key]
        print(f"  {key}  {e.get('category', '?'):15s}  ${e.get('amount', 0):>8.2f}  {e.get('splitType', '?'):10s}  {e.get('comment', '')}")


def cmd_add_split(args):
    init_firebase()
    created_at = int(datetime.datetime.now().timestamp() * 1000)
    ref = db.reference(f"weekly_totals/split/entries/{created_at}")
    data = {
        "category": args.category.upper(),
        "amount": args.amount,
        "comment": args.comment,
        "splitType": args.type,
        "createdByEmail": args.email,
        "createdAt": created_at,
    }
    ref.set(data)
    print(f"Added split: {data['category']} ${args.amount} ({args.type}) -> key {created_at}")


def cmd_tree(args):
    init_firebase()
    root = db.reference("weekly_totals").get() or {}
    for key in sorted(root.keys()):
        val = root[key]
        if isinstance(val, dict):
            print(f"  {key}/ ({len(val)} items)")
        else:
            print(f"  {key}: {val}")


def update_savings_for_week(week, amount_delta):
    """Adjust savings by amount_delta (negative = reduce savings)."""
    ref = db.reference(f"weekly_totals/savings/{week}")
    old = ref.get()
    if old is not None and isinstance(old, (int, float)):
        new_val = round(old + amount_delta, 2)
        ref.set(new_val)
        print(f"  Savings {week}: ${old} -> ${new_val}")


def main():
    parser = argparse.ArgumentParser(description="Firebase RTDB utility for Weekly Totals")
    sub = parser.add_subparsers(dest="command", required=True)

    # add-txn
    p = sub.add_parser("add-txn", help="Add a transaction")
    p.add_argument("--week", required=True, help="Week start date (yyyy-MM-dd)")
    p.add_argument("--category", required=True, help="Category name (e.g. GROCERIES)")
    p.add_argument("--amount", type=float, required=True, help="Amount in CAD")
    p.add_argument("--details", help="Optional details")
    p.add_argument("--key", type=int, help="Custom createdAt key (default: now)")
    p.add_argument("--adjustment", action="store_true", help="Mark as adjustment")
    p.add_argument("--update-savings", action="store_true", help="Also reduce savings for that week")
    p.set_defaults(func=cmd_add_txn)

    # list-txn
    p = sub.add_parser("list-txn", help="List transactions for a week")
    p.add_argument("--week", required=True, help="Week start date")
    p.set_defaults(func=cmd_list_txn)

    # del-txn
    p = sub.add_parser("del-txn", help="Delete a transaction by key")
    p.add_argument("--key", required=True, help="createdAt key")
    p.add_argument("--update-savings", action="store_true", help="Also restore savings")
    p.set_defaults(func=cmd_del_txn)

    # get-savings
    p = sub.add_parser("get-savings", help="View savings")
    p.add_argument("--week", help="Specific week (omit for all)")
    p.set_defaults(func=cmd_get_savings)

    # set-savings
    p = sub.add_parser("set-savings", help="Set savings for a week")
    p.add_argument("--week", required=True)
    p.add_argument("--amount", type=float, required=True)
    p.set_defaults(func=cmd_set_savings)

    # list-split
    p = sub.add_parser("list-split", help="List all split entries")
    p.set_defaults(func=cmd_list_split)

    # add-split
    p = sub.add_parser("add-split", help="Add a split entry")
    p.add_argument("--category", required=True)
    p.add_argument("--amount", type=float, required=True)
    p.add_argument("--comment", required=True)
    p.add_argument("--type", required=True, choices=["EQUAL", "I_OWE", "THEY_OWE", "SETTLEMENT"])
    p.add_argument("--email", required=True, help="Creator email")
    p.set_defaults(func=cmd_add_split)

    # tree
    p = sub.add_parser("tree", help="Show top-level database structure")
    p.set_defaults(func=cmd_tree)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
