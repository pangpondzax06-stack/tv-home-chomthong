# TV Home — จอมทอง

หน้า Home สำหรับทีวี TCL Android TV 11 (Smart TV, AR2101, แรมประมาณ 1 GB) ที่เขตจอมทอง กทม.
พื้นหลังท้องฟ้าเปลี่ยนตามเวลา (เช้า/กลางวัน/เย็น/ค่ำ/กลางคืน ตามเวลาพระอาทิตย์ขึ้น-ตกจริง) และตามสภาพอากาศ
พร้อมอุณหภูมิ โอกาสฝนตก และ **PM2.5 จากสถานี Air4Thai ที่ใกล้ที่สุด** อัปเดตทุกชั่วโมง

ดัดแปลงจาก [SkyGlass TV](https://github.com/safemook/skyglass-tv) ซึ่งแตกมาจาก
[CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT License)

## ปรับอะไรไปจากต้นฉบับ

| เรื่อง | ค่า |
|---|---|
| ท้องฟ้า/หมอกฝุ่น | `reduced_motion = true`: วาดเป็นภาพนิ่ง ไม่เคลื่อนไหว เพื่อไม่ให้ทีวีแรม 1 GB กระตุก ภาพเปลี่ยนเมื่อข้อมูลอากาศอัปเดตหรือกลับมาหน้า Home |
| Liquid glass | ปิด (ลดภาระ GPU) |
| ตำแหน่ง | จอมทอง 13.6773, 100.4846 เป็นค่าเริ่มต้น (เปลี่ยนได้ในแอป Air & Weather) |
| Dock | 3Plus, AIS Play, Ch7, Netflix, YouTube (5 คอลัมน์) |
| Live TV / Inputs | `com.tcl.tv` |
| ลายเซ็น APK | คีย์คงที่ใน `keystore/` เพื่อให้ APK รุ่นใหม่ติดตั้งทับรุ่นเก่าได้ |

## Build

GitHub Actions (`.github/workflows/build.yml`) build ทุกครั้งที่ push เข้า `main`
ได้ไฟล์ `tvhome-launcher.apk` และ `air-weather.apk` ใน branch **`apk`** และใน Artifacts ของแต่ละรอบ

## ติดตั้ง

ใช้สคริปต์ในโฟลเดอร์ `Documents\TCL-TV-Speedup` บนโน้ตบุ๊ก (`6_INSTALL_TVHOME.bat`)
ย้อนกลับไปใช้ Projectivy: `7_BACK_TO_PROJECTIVY.bat`

ข้อมูลอากาศ: [Open-Meteo](https://open-meteo.com) · ฝุ่น: [Air4Thai](http://air4thai.pcd.go.th) (กรมควบคุมมลพิษ)
