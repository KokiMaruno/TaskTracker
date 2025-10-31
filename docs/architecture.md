# TaskTracker - アーキテクチャ設計書

## プロジェクト概要

**名前**: TaskTracker
**目的**: タスク時間トラッキングアプリ（外部タスク管理ツール連携）
**特徴**: ドメイン駆動設計 + Clean Architecture

## 技術スタック

### バックエンド
- **言語**: C# (.NET 8)
- **Webフレームワーク**: ASP.NET Core
- **データアクセス**: Dapper（マイクロORM） + SqlKata（クエリビルダー）
- **データベース**: PostgreSQL
- **依存注入**: Microsoft.Extensions.DependencyInjection
- **マイグレーション**: FluentMigrator（Phase 2以降）

### フロントエンド（将来実装）
- **技術**: ClojureScript + Re-frame
- **結合**: RESTful API / JSON（疎結合）

### テスト
- **フレームワーク**: Nunit

### 開発環境
- **IDE**: JetBrains Rider
- **バージョン管理**: Git
- **開発用DB**: Docker + PostgreSQL

## アーキテクチャ原則

### レイヤードアーキテクチャ（クリーンアーキテクチャ）

```
┌─────────────────────────────────────┐
│  Handler (Presentation)             │  HTTPリクエスト処理
│  - Web Handlers                     │  JSONシリアライゼーション
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Application                        │  ユースケース実装
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  Domain                             │  ビジネスロジック
└─────────────────────────────────────┘
              ↑
┌─────────────────────────────────────┐
│  Infrastructure                     │  外部システム連携
│  - Database (Dapper)                │  データ永続化
│  - External API (HttpClient)        │  外部API呼び出し
└─────────────────────────────────────┘
```

### 依存関係の方向

```
Handler → Application → Domain ← Infrastructure
```

- **Domain**: 他のレイヤーに依存しない（純粋なビジネスロジック）
- **Application**: Domainのみに依存
- **Infrastructure**: Domainのインターフェースを実装
- **Handler**: Application + Infrastructureを組み合わせ

## プロジェクト構造

```
TaskTracker/
├── TaskTracker.sln                    # ソリューションファイル
├── src/
│   ├── TaskTracker.Domain/            # ドメイン層
│   │   - エンティティ型定義
│   │   - 基本型定義
│   │
│   ├── TaskTracker.Application/       # アプリケーション層
│   │   - ユースケース実装
│   │   - インターフェース定義
│   │
│   ├── TaskTracker.Infrastructure/    # インフラストラクチャ層
│   │   - Database/
│   │     - Repository実装
│   │     - DB接続管理
│   │   - ExternalApi/
│   │     - 外部APIクライアント実装
│   │
│   ├── TaskTracker.Migrations/    # マイグレーション管理
│   │
│   └── TaskTracker.Handler/           # ハンドラー層
│       - エントリーポイント
│       - HTTPハンドラー
│       - ルーティング定義
│       - DTO定義
│       - 設定ファイル
│
├── tests/
│   ├── TaskTracker.Domain.Tests/     # ドメイン層テスト
│   ├── TaskTracker.Application.Tests/ # アプリケーション層テスト
│   └── TaskTracker.Integration.Tests/ # 統合テスト
│
├── migrations/
│   └── 001_initial_schema.sql        # Phase 1: 手動SQL
│
└── docs/
    ├── architecture.md               # このファイル
    ├── domain-model.md               # ドメインモデル設計
    └── database-schema.md            # データベーススキーマ
```

## 各レイヤーの責務

### Domain層
**責務**: ビジネスロジックとドメインモデルの定義

- ドメイン概念の表現
- レコード型によるエンティティ定義
- ビジネスルールの実装
- **依存関係**: なし（純粋）

### Application層
**責務**: ユースケース実装とビジネスフローの制御

- ドメインモデルの組み合わせ
- トランザクション境界の定義
- **依存関係**: Domain層のみ

### Infrastructure層
**責務**: 外部システムとの連携

- データベースアクセス（Dapper + SqlKata）
- 外部API呼び出し（HttpClient）
- Applicationで定義されたインターフェースの実装
- **依存関係**: Domain層のインターフェース

### Handler層
**責務**: HTTPリクエスト/レスポンスの処理

- JSONシリアライゼーション
- DIコンテナの構成
- **依存関係**: Application + Infrastructure

## 設計原則

### 1. Make Illegal States Unrepresentable
不正な状態を型で表現不可能にする

### 3. 関心の分離
各レイヤーは明確な責務を持ち、他のレイヤーの詳細を知らない

### 4. 依存性逆転の原則
Infrastructure層はApplication層のインターフェースに依存

## マルチユーザー対応の準備

個人利用を前提としていますが、フォーク者が容易にマルチユーザー化できるよう設計しています。

### ID戦略: UUID

すべてのエンティティIDにGuid（UUID）を採用し、**Domain層で生成**：

**理由**:
- Domain層がエンティティのライフサイクルを制御（クリーンアーキテクチャ）
- 保存前にIDが確定（エンティティ間の関連付けが容易）
- テストが容易（DBなしでエンティティ生成可能）
- 分散システムへの拡張が可能
- マルチユーザー化時のデータ移行不要

### テナント分離の準備: user_id列

すべてのテーブルに`user_id UUID NULL`列を追加：

```sql
CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    user_id UUID NULL,  -- Phase 1では常にNULL
    ...
);
```

**マルチユーザー化時**:
- user_idを必須に変更
- すべてのクエリにWHERE user_id = @UserIdを追加
- Row Level Securityの導入

### 楽観的ロック: version列

すべてのテーブルに`version INT NOT NULL DEFAULT 0`列を追加：

```sql
CREATE TABLE tasks (
    ...
    version INT NOT NULL DEFAULT 0,
    ...
);
```

## 外部API連携

### 抽象化戦略
外部タスク管理ツール（GitLab等）への依存を最小化

### 実装の交換可能性
- GitLab実装
- Jira実装

## マイグレーション戦略

### Phase 1: 手動SQL
```bash
psql -d tasktracker -f migrations/001_initial_schema.sql
```

開発中は何度でもDROP/CREATEで作り直し可能。

### Phase 2以降: FluentMigrator

**実行方法**:
```bash
dotnet fm migrate up
```

## 設定管理

### appsettings.json
```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=tasktracker;Username=postgres;Password=postgres"
  },
  "ExternalApi": {
    "GitLab": {
      "BaseUrl": "https://gitlab.example.com",
      "Token": "..."
    }
  }
}
```

### 環境別設定
- `appsettings.Development.json`
- `appsettings.Production.json`

## セキュリティ考慮事項

- **API認証**: 将来実装（JWT等）
- **設定の秘匿**: 環境変数 / Azure Key Vault
- **SQLインジェクション対策**: Dapperのパラメータ化クエリ

## パフォーマンス考慮事項

- **コネクションプール**: Npgsqlのデフォルト設定
- **非同期処理**: Async/Task徹底
- **インデックス**: 後で最適化

## 今後の拡張性

### 想定される拡張
- 認証・認可
- WebSocket（リアルタイム更新）
- 高度な分析機能

### アーキテクチャの柔軟性
- 疎結合により、各レイヤーの独立した進化が可能
- インターフェースベースの設計により、実装の交換が容易
