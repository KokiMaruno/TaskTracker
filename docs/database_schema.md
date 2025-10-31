# TaskTracker - データベース設計

## 概要

リレーショナルデータベースを使用（PostgreSQL、SQLServer等に対応可能な設計）。

## 設計方針

### マルチユーザー対応の準備

- **user_id列**: すべての主要テーブルに追加（Phase 1では常にNULL）
- **version列**: 楽観的ロック用（Phase 5でロジック実装）

Phase 1では個人利用を前提とするが、Phase 5でマルチユーザー化時に以下を実施：
- user_idを必須に変更
- すべてのクエリにWHERE user_id = @UserIdを追加
- Row Level Security導入（PostgreSQLの場合）

### ID戦略

**UUID**: すべてのエンティティIDにGuidを使用
- Domain層で生成（`TaskId.create()`）
- データベースには生成済みUUIDを格納
- 分散システム対応、マルチユーザー化時のデータ移行不要

### データベース互換性

PostgreSQL、SQLServer、MySQL等で動作可能な設計を優先：
- 標準的なSQL型を使用
- DB固有機能（配列型等）は使用しない
- ラベルはカンマ区切り文字列で保存

### 正規化

- 基本的な正規化を適用
- パフォーマンス最適化は後で実施（Phase 4以降）

## 主要テーブル

### tasks

タスク情報を格納。

**主要カラム**:
- `id` (UUID, PK): Domain層で生成されたUUID
- `user_id` (UUID, NULL): Phase 5で使用
- `source` (VARCHAR): タスクの出自（Manual/GitLab/Jira/GitHub）
- `title` (VARCHAR): タスクタイトル
- `status` (VARCHAR): ステータス（Open/InProgress/Closed/Blocked）
- `labels` (VARCHAR): ラベル（カンマ区切り文字列）
- `version` (INT): 楽観的ロック用

### time_entries

時間記録を格納。

**主要カラム**:
- `id` (UUID, PK): Domain層で生成されたUUID
- `task_id` (UUID, FK): 外部キー（tasks.id）
- `user_id` (UUID, NULL): Phase 5で使用
- `start_time` (DATETIME): 開始時刻
- `end_time` (DATETIME, NULL): 終了時刻（Running時はNULL）
- `status` (VARCHAR): ステータス（Running/Completed）
- `version` (INT): 楽観的ロック用

## ビジネスルール実装

### データベース制約で保証

- **CHECK制約**: ステータス値の限定
- **UNIQUE制約**: 1タスク1実行中記録（部分インデックス）
- **外部キー制約**: 参照整合性

### アプリケーション層で保証

- タスクステータスの遷移ルール
- 時間記録の最小記録時間
- 外部システムとの同期ルール

## マイグレーション戦略

### Phase 1: 手動SQL

```
migrations/001_initial_schema.sql
```

開発中は何度でもDROP/CREATEで作り直し可能。

### Phase 2以降: FluentMigrator

```
src/TaskTracker.Infrastructure/Migrations/
```

FluentMigratorコードがスキーマ定義の真実（Single Source of Truth）。

## スキーマ定義の参照

実際のスキーマ定義（CREATE TABLE文、カラム定義、制約等）は以下を参照：
- **Phase 1**: `migrations/001_initial_schema.sql`
- **Phase 2以降**: `src/TaskTracker.Infrastructure/Migrations/`

## セキュリティ考慮事項

### Phase 1-4

- **接続文字列**: 環境変数で管理
- **SQLインジェクション**: Dapperのパラメータ化クエリで対策

### Phase 5（マルチユーザー化）

- **Row Level Security**: テーブルレベルのアクセス制御（PostgreSQL）
- **トークン暗号化**: 外部API連携用トークンの暗号化
- **監査ログ**: 重要な操作のログ記録