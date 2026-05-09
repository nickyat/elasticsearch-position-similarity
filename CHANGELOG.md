# Changelog

## 9.4.0

- Added support for Elasticsearch 9.4.0.
- Migrated query builder code to the Elasticsearch 9 query API.
- Migrated scoring code to Lucene 10 `Query`, `Weight`, `Scorer`, and term vector APIs.
- Updated Maven build plugins and switched to `maven.compiler.release=17`.
- Kept Elasticsearch dependencies as `provided` so the plugin ZIP contains only the plugin jar and descriptor.
- Added Docker-based smoke test for `position_match`.

Known note: Elasticsearch 9.4.0 prints `plugin has a policy file with no additional entitlements` when installing this classic plugin. The plugin ZIP does not contain `plugin-security.policy` or `entitlement-policy.yaml`, and the warning does not block installation.
