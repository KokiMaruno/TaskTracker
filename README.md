# TaskTracker

外部タスク管理ツール（GitLab/Jira/GitHub等）と連携する、時間トラッキングアプリケーション

## 概要

TaskTrackerは、複数のタスク管理ツールを横断して統一された時間記録を提供します。外部ツールでタスクを管理しながら、TaskTrackerで時間を記録することで、業務時間の可視化と分析が可能になります。

### 核心的な価値

- **統一された時間記録**: 複数のツール（GitLab/Jira/GitHub）を横断した時間トラッキング
- **タスク情報源の統合**: 外部ツールからの同期 + 手動作成の一元管理
- **業務カテゴリ分析**: ラベルベースの業務分類による時間配分の可視化
- **非侵襲的な追加**: 既存ワークフローを変えず、時間記録のみを上乗せ

## 主要な設計判断

### アーキテクチャ

- **クリーンアーキテクチャ**: Domain / Application / Infrastructure / Handler の4層構造
- **ドメイン駆動設計**: 型で不正な状態を排除、ビジネスルールをDomain層に集約

### 技術スタック

**バックエンド**:
- 言語: C# (.NET 8)
- Webフレームワーク: ASP.NET Core
- データベース: PostgreSQL
- データアクセス: Dapper + SqlKata
- マイグレーション: FluentMigrator

**フロントエンド** (将来):
- ClojureScript + Re-frame
- RESTful API経由で疎結合

### マルチユーザー対応の準備

個人利用を前提としていますが、フォーク者が容易にマルチユーザー化できるよう設計しています：

- **UUID**: すべてのエンティティIDにGuidを採用（分散システム対応）
- **user_id列**: 将来のテナント分離用（Phase 1では常にNULL）
- **version列**: 楽観的ロック用（Phase 5で実装予定）

## 現在の状態

**実装フェーズ**: Phase 1 準備完了  
**ステータス**: 設計完了、実装開始前

### 実装計画

- **Phase 1** (1週間): 基本CRUD - タスクの作成・更新・削除
- **Phase 2** (1週間): 時間記録 - 開始・停止・履歴取得
- **Phase 3** (1週間): 外部API連携 - GitLabからのタスク同期
- **Phase 4** (1週間): 分析機能 - 集計・グラフデータ
- **Phase 5** (1週間): テスト整備 - カバレッジ向上、マルチユーザー化対応

## セットアップ

### 前提条件

- .NET 8 SDK
- PostgreSQL 14以降
- JetBrains Rider または VS Code

### データベース準備

```bash
# PostgreSQLデータベース作成
createdb tasktracker

# Phase 1: 手動でマイグレーション実行
psql -d tasktracker -f migrations/001_initial_schema.sql
```

### アプリケーション起動

```bash
# 依存関係の復元
dotnet restore

# アプリケーション実行
dotnet run --project src/TaskTracker.Handler
```

## プロジェクト構造

```
TaskTracker/
├── docs/                      # 設計ドキュメント
│   ├── architecture.md        # アーキテクチャ設計書
│   └── database-schema.md     # データベーススキーマ
├── src/
│   ├── TaskTracker.Domain/    # ドメイン層（純粋なビジネスロジック）
│   ├── TaskTracker.Application/ # アプリケーション層（ユースケース）
│   ├── TaskTracker.Infrastructure/ # インフラ層（DB、外部API）
│   └── TaskTracker.Handler/   # ハンドラー層（HTTP）
├── tests/
│   ├── TaskTracker.Domain.Tests/
│   ├── TaskTracker.Application.Tests/
│   └── TaskTracker.Integration.Tests/
└── migrations/                # データベースマイグレーション
```

## コアドメインモデル

### Task（タスク）

タスクは複数のソースから生成されます：

- **Manual**: 手動作成（外部ツールに依存しない）
- **GitLab**: GitLab Issueから同期
- **Jira**: Jira Issueから同期（将来実装）
- **GitHub**: GitHub Issueから同期（将来実装）

各タスクは以下の情報を持ちます：

- ID（UUID、Domain層で生成）
- タイトル、説明
- ステータス（Open/InProgress/Closed/Blocked）
- ラベル（カンマ区切り文字列）
- 外部システム情報（外部タスクの場合）
- ローカルメモ（外部システムに影響しない）

### TimeEntry（時間記録）

- タスクに対する時間記録
- 開始時刻、終了時刻、所要時間
- 1タスクに実行中の記録は1つのみ（ビジネスルール）

## API設計

RESTful API（JSON）

**タスク**:
- `GET /api/tasks` - タスク一覧
- `GET /api/tasks/{id}` - タスク詳細
- `POST /api/tasks` - タスク作成
- `PUT /api/tasks/{id}` - タスク更新

**時間記録**:
- `POST /api/tasks/{id}/start-tracking` - 時間記録開始
- `POST /api/time-entries/{id}/stop` - 時間記録停止
- `GET /api/tasks/{id}/time-entries` - タスク別時間記録一覧

**外部連携** (Phase 3以降):
- `POST /api/sync/projects/{id}/tasks` - 外部タスク同期
- `POST /api/tasks/{id}/close-external` - 外部タスククローズ

## ライセンス

MIT License - 詳細は[LICENSE](LICENSE)を参照
