-- Remove the feature_flags table. It only ever held
-- 'explorer.player_history_visible', a stopgap privacy gate now replaced by
-- in-app RBAC in treasury-rest-api (see AUTH-SPEC.md §6). The FeatureFlagService
-- and /api/v1/explorer/feature-flags endpoint were removed alongside this.
DROP TABLE IF EXISTS feature_flags;
