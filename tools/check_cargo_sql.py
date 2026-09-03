#!/usr/bin/env python3
"""Smoke-check the shipped v4 schema, v5 SQL migration and DAO SQL with SQLite.

This does not replace Room/Android instrumentation. It runs without Android SDK:
    python tools/check_cargo_sql.py
"""
import json
from pathlib import Path
import re
import sqlite3
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/br/com/usinagemmaster'
SCHEMA = json.loads((ROOT / 'app/schemas/br.com.usinagemmaster.data.local.database.GameDatabase/4.json').read_text())['database']
MODULE = (JAVA / 'di/DatabaseModule.kt').read_text().split('val MIGRATION_4_5 =', 1)[1].split('@Provides', 1)[0]
MIGRATION = [a or b for a, b in re.findall(r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', MODULE, re.S)]
QUERIES = re.findall(r'@Query\("([^"\n]*)"\)', (JAVA / 'data/local/dao/ProductionCargoDao.kt').read_text())

def query(prefix):
    return next(q for q in QUERIES if q.startswith(prefix))

def prepare(db):
    for entity in SCHEMA['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    db.execute("INSERT INTO company VALUES (1, 'Save antigo', 123456, 8, 2, 99, 100, 0, 600000, 0)")
    # Keep a non-empty legacy machine table to detect accidental destructive migration.
    entity = next(e for e in SCHEMA['entities'] if e['tableName'] == 'machines')
    values = ['old-machine' if f['columnName'] == 'id' else ('legacy' if f['affinity'] == 'TEXT' else 1) for f in entity['fields']]
    db.execute('INSERT INTO machines VALUES (' + ','.join('?' for _ in values) + ')', values)
    db.commit()
    assert len(MIGRATION) == 2
    with db:
        for sql in MIGRATION:
            db.execute(sql)


def stage(db, id, value=700):
    with db:
        db.execute('INSERT INTO production_cargo VALUES (?, ?, 5000, 1, 600000, NULL)', (id, value))


def deliver_sql(db, ids, receipt='receipt'):
    """Exercise the DAO's SQL order in a native transaction (not a Kotlin execution)."""
    distinct = list(dict.fromkeys(ids))
    chunks = [distinct[i:i + 200] for i in range(0, len(distinct), 200)]
    with db:
        selected = []
        for chunk in chunks:
            selected += db.execute(query('SELECT * FROM production_cargo WHERE deliveredAt IS NULL AND').replace(':ids', ','.join('?' for _ in chunk)), chunk).fetchall()
        if not selected:
            return 0
        value = sum(row[1] for row in selected)
        balance = db.execute(query('SELECT cashCents')).fetchone()
        if balance is None:
            raise ValueError('Missing company')
        if value < 0 or balance[0] + value > 2**63 - 1:
            raise OverflowError()
        for chunk in chunks:
            db.execute(query('UPDATE production_cargo').replace(':now', '?').replace(':ids', ','.join('?' for _ in chunk)), [700000] + chunk)
        db.execute(query('UPDATE company'), {'amount': value})
        db.execute("INSERT INTO financial_transactions VALUES (?, 'INCOME', 'PRODUCTION', ?, 'Entrega', 700000)", (receipt, value))
        return value


class CargoSqlChecks(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        prepare(self.db)

    def tearDown(self):
        self.db.close()

    def pending(self):
        return self.db.execute(query('SELECT * FROM production_cargo WHERE deliveredAt IS NULL ORDER')).fetchall()

    def balance(self):
        return self.db.execute(query('SELECT cashCents')).fetchone()[0]

    def test_migration_preserves_old_save(self):
        self.assertEqual(self.balance(), 123456)
        self.assertEqual(self.db.execute('SELECT lastSimulationAt FROM company').fetchone()[0], 600000)
        self.assertEqual(self.db.execute('SELECT id FROM machines').fetchone()[0], 'old-machine')
        self.assertEqual(self.pending(), [])
        columns = {r[1]: r for r in self.db.execute('PRAGMA table_info(production_cargo)')}
        self.assertEqual(set(columns), {'id', 'valueCents', 'unitsMilli', 'cycles', 'createdAt', 'deliveredAt'})
        self.assertEqual(columns['deliveredAt'][3], 0)
        self.assertTrue(any(r[1] == 'index_production_cargo_deliveredAt' for r in self.db.execute('PRAGMA index_list(production_cargo)')))

    def test_no_cash_until_delivery_and_replay_pays_once(self):
        stage(self.db, 'one')
        self.assertEqual(self.balance(), 123456)
        self.assertEqual(deliver_sql(self.db, ['one', 'one']), 700)
        self.assertEqual(deliver_sql(self.db, ['one']), 0)
        self.assertEqual(self.balance(), 124156)
        self.assertEqual(self.pending(), [])
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM financial_transactions').fetchone()[0], 1)

    def test_new_load_waits_for_next_trip(self):
        stage(self.db, 'one')
        trip = [r[0] for r in self.pending()]
        stage(self.db, 'later', 300)
        self.assertEqual(deliver_sql(self.db, trip), 700)
        self.assertEqual([r[0] for r in self.pending()], ['later'])

    def test_receipt_failure_rolls_back_everything(self):
        stage(self.db, 'one')
        self.db.execute("CREATE TRIGGER reject_receipt BEFORE INSERT ON financial_transactions BEGIN SELECT RAISE(ABORT, 'test'); END")
        with self.assertRaises(sqlite3.IntegrityError):
            deliver_sql(self.db, ['one'])
        self.assertEqual(self.balance(), 123456)
        self.assertEqual(len(self.pending()), 1)
        self.db.execute('DROP TRIGGER reject_receipt')
        self.assertEqual(deliver_sql(self.db, ['one']), 700)

    def test_large_queue_chunks_and_overflow_preserve_balance(self):
        for i in range(1101):
            stage(self.db, str(i), 10)
        self.assertEqual(deliver_sql(self.db, [str(i) for i in range(1101)]), 11010)
        stage(self.db, 'overflow', 2**63 - 1)
        before = self.balance()
        with self.assertRaises(OverflowError):
            deliver_sql(self.db, ['overflow'], 'overflow-receipt')
        self.assertEqual(self.balance(), before)
        self.assertEqual(len(self.pending()), 1)

    def test_missing_company_keeps_pending_cargo(self):
        stage(self.db, 'one')
        with self.db:
            self.db.execute('DELETE FROM company')
        with self.assertRaises(ValueError):
            deliver_sql(self.db, ['one'])
        self.assertEqual(len(self.pending()), 1)

    def test_unfinished_trip_survives_database_reopen(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = str(Path(tmp) / 'save.db')
            db = sqlite3.connect(file)
            prepare(db)
            stage(db, 'saved')
            db.close()
            db = sqlite3.connect(file)
            self.assertEqual(deliver_sql(db, ['saved']), 700)
            db.close()
            db = sqlite3.connect(file)
            self.assertEqual(deliver_sql(db, ['saved']), 0)
            self.assertEqual(db.execute(query('SELECT cashCents')).fetchone()[0], 124156)
            db.close()


if __name__ == '__main__':
    unittest.main(verbosity=2)
