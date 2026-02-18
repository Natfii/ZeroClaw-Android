/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

//! Shared type helpers for FFI boundary conversions.

/// Converts a [`chrono::DateTime<Utc>`] to epoch milliseconds.
///
/// Returns the number of milliseconds since the Unix epoch.
pub(crate) fn to_epoch_ms(dt: &chrono::DateTime<chrono::Utc>) -> i64 {
    dt.timestamp_millis()
}

/// Converts an optional [`chrono::DateTime<Utc>`] to optional epoch milliseconds.
pub(crate) fn opt_to_epoch_ms(dt: &Option<chrono::DateTime<chrono::Utc>>) -> Option<i64> {
    dt.as_ref().map(to_epoch_ms)
}

#[cfg(test)]
#[allow(clippy::unwrap_used)]
mod tests {
    use super::*;
    use chrono::{TimeZone, Utc};

    #[test]
    fn test_to_epoch_ms() {
        let dt = Utc.with_ymd_and_hms(2026, 1, 1, 0, 0, 0).unwrap();
        assert_eq!(to_epoch_ms(&dt), 1_767_225_600_000);
    }

    #[test]
    fn test_opt_to_epoch_ms_some() {
        let dt = Some(Utc.with_ymd_and_hms(2026, 1, 1, 0, 0, 0).unwrap());
        assert!(opt_to_epoch_ms(&dt).is_some());
    }

    #[test]
    fn test_opt_to_epoch_ms_none() {
        assert!(opt_to_epoch_ms(&None).is_none());
    }
}
