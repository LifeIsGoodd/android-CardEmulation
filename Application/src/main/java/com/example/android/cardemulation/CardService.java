/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.cardemulation;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import com.example.android.common.logger.Log;

import java.util.Arrays;


/**
 * This is a sample APDU Service which demonstrates how to interface with the card emulation support
 * added in Android 4.4, KitKat.
 *
 * <p>This sample replies to any requests sent with the string "Hello World". In real-world
 * situations, you would need to modify this code to implement your desired communication
 * protocol.
 *
 * <p>This sample will be invoked for any terminals selecting AIDs of 0xF11111111, 0xF22222222, or
 * 0xF33333333. See src/main/res/xml/aid_list.xml for more details.
 *
 * <p class="note">Note: This is a low-level interface. Unlike the NdefMessage many developers
 * are familiar with for implementing Android Beam in apps, card emulation only provides a
 * byte-array based communication channel. It is left to developers to implement higher level
 * protocol support as needed.
 */
public class CardService extends HostApduService {
    private static final String TAG = "CardService";
    // AID for our loyalty card service.
    private static final String SAMPLE_LOYALTY_CARD_AID = "F222222222";
    // ISO-DEP command HEADER for selecting an AID.
    // Format: [Class | Instruction | Parameter 1 | Parameter 2]
    private static final String SELECT_APDU_HEADER = "00A40400";
    // "OK" status word sent in response to SELECT AID command (0x9000)
    private static final byte[] SELECT_OK_SW = HexStringToByteArray("9000");
    // "UNKNOWN" status word sent in response to invalid APDU command (0x0000)
    private static final byte[] UNKNOWN_CMD_SW = HexStringToByteArray("0000");
    private static final byte[] SELECT_APDU = BuildSelectApdu(SAMPLE_LOYALTY_CARD_AID);
    private static final byte[] VERIFY_APDU = new byte[] {(byte)0x80, (byte)0x20, (byte)0x00, (byte)0x00};


    /**
     * Called if the connection to the NFC card is lost, in order to let the application know the
     * cause for the disconnection (either a lost link, or another AID being selected by the
     * reader).
     *
     * @param reason Either DEACTIVATION_LINK_LOSS or DEACTIVATION_DESELECTED
     */
    @Override
    public void onDeactivated(int reason) { }

    /**
     * This method will be called when a command APDU has been received from a remote device. A
     * response APDU can be provided directly by returning a byte-array in this method. In general
     * response APDUs must be sent as quickly as possible, given the fact that the user is likely
     * holding his device over an NFC reader when this method is called.
     *
     * <p class="note">If there are multiple services that have registered for the same AIDs in
     * their meta-data entry, you will only get called if the user has explicitly selected your
     * service, either as a default or just for the next tap.
     *
     * <p class="note">This method is running on the main thread of your application. If you
     * cannot return a response APDU immediately, return null and use the {@link
     * #sendResponseApdu(byte[])} method later.
     *
     * @param commandApdu The APDU that received from the remote device
     * @param extras A bundle containing extra data. May be null.
     * @return a byte-array containing the response APDU, or null if no response APDU can be sent
     * at this point.
     */
    // BEGIN_INCLUDE(processCommandApdu)
    /*
    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        Log.i(TAG, "Received APDU: " + ByteArrayToHexString(commandApdu));
        // If the APDU matches the SELECT AID command for this service,
        // send the loyalty card account number, followed by a SELECT_OK status trailer (0x9000).
        if (Arrays.equals(SELECT_APDU, commandApdu)) {
            String account = AccountStorage.GetAccount(this);
            byte[] accountBytes = account.getBytes();
            Log.i(TAG, "Sending account number: " + account);
            return ConcatArrays(accountBytes, SELECT_OK_SW);
        } else {
            return UNKNOWN_CMD_SW;
        }
    }
    */
  @Override
  public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
      Log.i(TAG, "Received APDU: " + ByteArrayToHexString(commandApdu));

      // Проверка 1: Если пришла стандартная команда SELECT AID (уже была в проекте)
      if (cmp_array(SELECT_APDU, commandApdu, SELECT_APDU.length)) {
        String account = AccountStorage.GetAccount(this);
        byte[] accountBytes = account.getBytes();
        Log.i(TAG, "Sending account number: " + account);
        return ConcatArrays(accountBytes, SELECT_OK_SW); // Возвращает данные + 90 00
      }
    
      // Проверка 2: Наша новая кастомная команда (Запрос-Ответ)
      // Проверяем первые 4 байта входящего пакета (Класс, Инструкция, P1, P2)
      else if (cmp_array(VERIFY_APDU, commandApdu, VERIFY_APDU.length)) {
        Log.i(TAG, "Обнаружена кастомная команда VERIFY!");
        
        // Извлекаем полезные данные, которые прислал ридер (начиная с 5-го байта)
        // commandApdu[4] - это байт длины данных Lc
        int dataLength = commandApdu[4] & 0xFF; // Безопасное приведение byte к int
        byte[] receivedData = new byte[dataLength];
        System.arraycopy(commandApdu, 5, receivedData, 0, dataLength);
        
        // +++ ТУТ НАША ЛОГИКА ОБРАБОТКИ (ЗАПРОС -> ОТВЕТ) +++

        // Например разворачиваем (реверсируем) полученные данные задом наперед.
        // Создаем массив нужного размера под ответ (копия длины)
        byte[] successPayload = new byte[dataLength];

        for (int i = 0, j = dataLength - 1; i < dataLength; i++, j--) {
          successPayload[i] = receivedData[j];
        }
        return ConcatArrays(successPayload, SELECT_OK_SW);

        /*
        // Например, проверим, прислал ли ридер правильный секрет 11 22 33 44
        byte[] expectedSecret = new byte[] {0x11, 0x22, 0x33, 0x44};
        
        if (Arrays.equals(receivedData, expectedSecret)) {
            Log.i(TAG, "ПИН-код от ридера ВЕРНЫЙ!");
            // Формируем ответ: например, байт 0x01 (Доступ разрешен) + статус 90 00
            byte[] successPayload = new byte[] { 0x01 };
            return ConcatArrays(successPayload, SELECT_OK_SW);
        } else {
            Log.i(TAG, "ПИН-код от ридера НЕВЕРНЫЙ!");
            // Возвращаем байт 0x00 (Отказ) + статус 90 00
            byte[] failPayload = new byte[] { 0x00 };
            return ConcatArrays(failPayload, SELECT_OK_SW);
        }
        */        
        // --- ТУТ НАША ЛОГИКА ОБРАБОТКИ (ЗАПРОС -> ОТВЕТ) ---

      }

      // Если ридер прислал любую другую неизвестную команду
      else {
        Log.w(TAG, "Неизвестная APDU команда");
        return UNKNOWN_CMD_SW; // Возвращает статус ошибки 0x00 0x00 или 0x6F 0x00
      }
}

    // END_INCLUDE(processCommandApdu)

    /**
     * Build APDU for SELECT AID command. This command indicates which service a reader is
     * interested in communicating with. See ISO 7816-4.
     *
     * @param aid Application ID (AID) to select
     * @return APDU for SELECT AID command
     */
    public static byte[] BuildSelectApdu(String aid) {
        // Format: [CLASS | INSTRUCTION | PARAMETER 1 | PARAMETER 2 | LENGTH | DATA]
        return HexStringToByteArray(SELECT_APDU_HEADER + String.format("%02X",
                aid.length() / 2) + aid);
    }

    /**
     * Utility method to convert a byte array to a hexadecimal string.
     *
     * @param bytes Bytes to convert
     * @return String, containing hexadecimal representation.
     */
    public static String ByteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2]; // Each byte has two hex characters (nibbles)
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF; // Cast bytes[j] to int, treating as unsigned value
            hexChars[j * 2] = hexArray[v >>> 4]; // Select hex character from upper nibble
            hexChars[j * 2 + 1] = hexArray[v & 0x0F]; // Select hex character from lower nibble
        }
        return new String(hexChars);
    }

    /**
     * Utility method to convert a hexadecimal string to a byte string.
     *
     * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
     *
     * @param s String containing hexadecimal characters to convert
     * @return Byte array generated from input
     * @throws java.lang.IllegalArgumentException if input length is incorrect
     */
    public static byte[] HexStringToByteArray(String s) throws IllegalArgumentException {
        int len = s.length();
        if (len % 2 == 1) {
            throw new IllegalArgumentException("Hex string must have even number of characters");
        }
        byte[] data = new byte[len / 2]; // Allocate 1 byte per 2 hex characters
        for (int i = 0; i < len; i += 2) {
            // Convert each character into a integer (base-16), then bit-shift into place
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Utility method to concatenate two byte arrays.
     * @param first First array
     * @param rest Any remaining arrays
     * @return Concatenated copy of input arrays
     */
    public static byte[] ConcatArrays(byte[] first, byte[]... rest) {
        int totalLength = first.length;
        for (byte[] array : rest) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (byte[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

     public boolean cmp_array(byte[] first, byte[] second, int len) {
       // Проверяем, что массивы не null и их длина не меньше требуемой
       // Если этой проверки в коде не будет, то программа попытается узнать длину у пустого места (null.length). 
       // В этот момент приложение намертво зависнет, операционная система Android выдаст ошибку «Приложение CardEmulation остановилось», 
       // а ваш телефон вообще перестанет реагировать на замок до тех пор, пока вы вручную не перезапустите программу.
       if (first == null || second == null) return false;
       if (first.length < len) return false;
       if (second.length < len) return false;

       // Побайтное сравнение до указанной длины
       for (int i = 0; i < len; i++) {
           /*
         // Форматируем байты в красивый HEX-вид (например, "0xAB")
         String hexFirst = String.format("0x%02X", first[i]);
         String hexSecond = String.format("0x%02X", second[i]);
         // Выводим в лог текущий индекс и сравниваемые байты
         Log.i(logTag, "Индекс [" + i + "]: Шаблон = " + hexFirst + ", Прилетело = " + hexSecond);
           */
           Log.i("CardService_CMP", "Индекс " + i + ": " + first[i] + " == " + second[i]);
           
         if (first[i] != second[i]) {
           return false; // Нашли несовпадение — сразу выходим
         }
      }

      return true; // Если весь цикл прошел без ошибок, значит массивы равны
    }    
}
