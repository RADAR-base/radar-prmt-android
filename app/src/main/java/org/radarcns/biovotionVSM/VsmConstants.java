/*
 * Copyright 2015 HES-SO Valais-Wallis, HEI, Infotronics. All rights reserved.
 */

package org.radarcns.biovotionVSM;

import ch.hevs.biovotion.vsm.core.VsmConnectionState;
import ch.hevs.biovotion.vsm.core.VsmDevice;
import ch.hevs.biovotion.vsm.core.VsmDeviceListener;
import ch.hevs.ble.lib.scanner.DiscoveredEntity;

/**
 * Define some constants used in several activities and views.
 *
 * @author Christopher Métrailler (mei@hevs.ch)
 * @version 1.0 - 2016/07/20
 */
public final class VsmConstants {

  /**
   * Key used to pass the {@link DiscoveredEntity} descriptor as intent argument.
   * The descriptor of the discovered VSM device is serialized and passed between activities.
   */
  public final static String KEY_DESC_EXTRA = "key_extra_desc";

  /**
   * Default Bluetooth connection timeout. If this timeout is reached, the callback
   * {@link VsmDeviceListener#onVsmDeviceConnectionError(VsmDevice, VsmConnectionState)} is called.
   * <p/>
   * The default timeout on Android is 30 seconds. Using the BLE library, the maximum timeout value
   * is fixed to 25 seconds.
   * When connecting the first time to a VSM device, the connection time can be up to 5 seconds because the pairing
   * process can take time and it is based on some internal delays.
   */
  public final static int BLE_CONN_TIMEOUT_MS = 10000;
  
  private VsmConstants() {
    // Private
  }
}
