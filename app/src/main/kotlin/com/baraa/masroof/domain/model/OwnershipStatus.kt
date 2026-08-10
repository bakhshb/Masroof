package com.baraa.masroof.domain.model

/**
 * Whether a known account or card belongs to the user.
 *
 * Must not be inferred solely from bank wording such as "internal transfer".
 */
enum class OwnershipStatus {
    /** Belongs to the user. */
    OWNED,

    /** Known not to belong to the user. */
    EXTERNAL,

    /** Not yet resolved. */
    UNKNOWN,
}
