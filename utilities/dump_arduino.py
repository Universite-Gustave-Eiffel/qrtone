# pip install pyserial
import serial


ser = serial.Serial('COM3', 2000000)

ser.close()
ser.open()

SAMPLE_RATE = 16000

SAMPLE_SIZE = 4

total_read = int(SAMPLE_RATE * SAMPLE_SIZE * 20.0)
total_left = total_read
last_perc = 0
try:
    with open("arduino_audio.raw", 'wb') as f:
        try:
            while total_left > 0:
                f.write(ser.read(512))
                total_left -= 512
                new_perc = int(((total_read - total_left) / float(total_read)) * 100.0)
                if new_perc - last_perc > 5:
                    last_perc = new_perc
                    print("%d %%" % new_perc)
        finally:
            f.flush()
finally:
    ser.close()

