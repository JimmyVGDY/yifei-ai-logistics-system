# 物流管理平台 — 存量敏感数据加密迁移脚本
# 用法：python encrypt-existing-data.py (需安装 pymysql + cryptography: pip install pymysql cryptography)
# 将数据库中的手机号字段批量加密为 ENC: 格式（AES/ECB/PKCS5Padding，与 FieldEncryptor.java 一致）

import pymysql
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import padding
import base64

KEY = '***'  # 开发环境默认密钥，生产改为 APP_ENCRYPT_KEY
ENC_PREFIX = 'ENC:'
BLOCK_SIZE = 128  # AES block size in bits

def encrypt(plain):
    if not plain or plain.startswith(ENC_PREFIX):
        return plain
    key = KEY.encode('utf-8')[:16]
    padder = padding.PKCS7(BLOCK_SIZE).padder()
    padded = padder.update(plain.encode('utf-8')) + padder.finalize()
    cipher = Cipher(algorithms.AES(key), modes.ECB(), backend=default_backend())
    encryptor = cipher.encryptor()
    result = encryptor.update(padded) + encryptor.finalize()
    return ENC_PREFIX + base64.b64encode(result).decode()

conn = pymysql.connect(host='127.0.0.1', user='root', password='', database='logistics_management')
cursor = conn.cursor()

tables_cols = [
    ('sys_user', ['mobile']),
    ('logistics_driver', ['phone']),
    ('logistics_customer', ['contact_phone']),
    ('logistics_warehouse', ['contact_phone']),
]

for table, columns in tables_cols:
    for col in columns:
        cursor.execute(f"SELECT id, {col} FROM {table} WHERE {col} IS NOT NULL AND {col} != '' AND {col} NOT LIKE 'ENC:%%'")
        rows = cursor.fetchall()
        for row in rows:
            encrypted = encrypt(row[1])
            cursor.execute(f"UPDATE {table} SET {col}=%s WHERE id=%s", (encrypted, row[0]))
        print(f"  {table}.{col}: {len(rows)} 条已加密")

conn.commit()
conn.close()
print("✅ 存量数据加密完成")
