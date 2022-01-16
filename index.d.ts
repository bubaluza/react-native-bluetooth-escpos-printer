declare module 'react-native-bluetooth-escpos-printer' {
    export interface PairableDevice {
        address: string;
        name?: string;
    }

    export interface BluetoothStatus {
        status: boolean;
        paired?: Array<PairableDevice>;
    }

    export interface ScanResults {
        paired: Array<PairableDevice>;
        found: Array<PairableDevice>;
    }

    export interface PairedDevice {
        paired: Array<PairableDevice>;
    }

    namespace BluetoothManager {
        export function isBluetoothEnabled(): Promise<BluetoothStatus>;
        export function scanDevices(): Promise<ScanResults>;
        export function disableBluetooth(): Promise<boolean>;
        export function enableBluetooth(): Promise<PairedDevice | null>;
        export function connect(address: string): Promise<string>;
        export function unpaire(address: string): Promise<string>;
        export function isDeviceConnected(): Promise<boolean>;
        export function getConnectedDeviceAddress(): Promise<string>;
        export const EVENT_DEVICE_ALREADY_PAIRED: string;
        export const EVENT_DEVICE_FOUND: string;
        export const EVENT_BLUETOOTH_NOT_SUPPORT: string;
        export const EVENT_CONNECTED: string;
        export const EVENT_CONNECTION_LOST: string;
    }

    export interface TextConfig {
        widthtimes?: number;
        heigthtimes?: number;
        fonttype?: number;
        encoding?: 'GBK' | 'Cp1256';
        codepage?: number;
    }

    namespace BluetoothEscposPrinter {
        export const EVENT_DEVICE_ALREADY_PAIRED: string;
        export const EVENT_DEVICE_FOUND: string;
        export const EVENT_BLUETOOTH_NOT_SUPPORT: string;

        export interface ALIGN {
            LEFT: number;
            CENTER: number;
            RIGHT: number;
        }
        export function printerInit(): Promise<void>;
        export function printText(
            text: string,
            options?: TextConfig,
        ): Promise<void>;

        export function printerAlign(_align: number): Promise<void>;
    }

    namespace BluetoothTscPrinter {}

    export {BluetoothManager, BluetoothEscposPrinter, BluetoothTscPrinter};
}
