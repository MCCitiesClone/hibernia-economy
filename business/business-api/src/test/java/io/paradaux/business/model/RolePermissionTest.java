package io.paradaux.business.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolePermissionTest {

    @Test
    void exactNameMatchesIgnoringCase() {
        assertThat(RolePermission.fromString("ADMIN")).isEqualTo(RolePermission.ADMIN);
        assertThat(RolePermission.fromString("admin")).isEqualTo(RolePermission.ADMIN);
        assertThat(RolePermission.fromString("Financial")).isEqualTo(RolePermission.FINANCIAL);
    }

    @Test
    void aliasChestUnderscoreShop() {
        assertThat(RolePermission.fromString("CHEST_SHOP")).isEqualTo(RolePermission.CHESTSHOP);
        assertThat(RolePermission.fromString("chest_shop")).isEqualTo(RolePermission.CHESTSHOP);
    }

    @Test
    void aliasAdministrator() {
        assertThat(RolePermission.fromString("ADMINISTRATOR")).isEqualTo(RolePermission.ADMIN);
        assertThat(RolePermission.fromString("Administrator")).isEqualTo(RolePermission.ADMIN);
    }

    @Test
    void leadingTrailingWhitespaceIsTrimmed() {
        assertThat(RolePermission.fromString("  ADMIN  ")).isEqualTo(RolePermission.ADMIN);
    }

    @Test
    void unknownPermissionThrows() {
        assertThatThrownBy(() -> RolePermission.fromString("MOON"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankStringThrows() {
        assertThatThrownBy(() -> RolePermission.fromString("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyStringThrows() {
        assertThatThrownBy(() -> RolePermission.fromString(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullThrows() {
        assertThatThrownBy(() -> RolePermission.fromString(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
