#!/bin/bash
# DynamoDB Local에 ai_reports 테이블 생성 스크립트
# docker-compose up 이후 한 번만 실행하면 됩니다.

echo "DynamoDB Local에 ai_reports 테이블 생성 중..."

aws dynamodb create-table \
  --table-name ai_reports \
  --attribute-definitions \
    AttributeName=PK,AttributeType=S \
    AttributeName=SK,AttributeType=S \
  --key-schema \
    AttributeName=PK,KeyType=HASH \
    AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8001 \
  --region ap-northeast-2 \
  --no-cli-pager

echo ""
echo "생성된 테이블 확인:"
aws dynamodb list-tables \
  --endpoint-url http://localhost:8001 \
  --region ap-northeast-2 \
  --no-cli-pager
