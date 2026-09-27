// Generates a realistic Una Watch MTB activity with the Una SDK's own FIT encoder.
#include "SDK/Fit/FitProfile.hpp"
#include "SDK/Fit/FitWriter.hpp"
#include "FakeFileSystem.hpp"
#include <cmath>
#include <cstdio>
#include <string>
namespace fit = SDK::Fit;
int main() {
    SDK::Test::FakeFileSystem fs;
    auto file = fs.file("una.fit");
    file->open(true, true);
    fit::FitWriter w(*file);
    w.begin(2147);
    using namespace fit::field;
    const uint32_t t0 = 1'758'960'000u - 631'065'600u; // 2025-09-27 07:46Z in FIT time
    w.defineMessage(0, fit::mesgNum(fit::MesgNum::FileId), {FileId::Type, FileId::Manufacturer, FileId::Product, FileId::SerialNumber, FileId::TimeCreated});
    w.data(0).u8((uint8_t)fit::File::Activity).u16(351).u16(1).u32(424242u).u32(t0).write();
    w.defineMessage(1, fit::mesgNum(fit::MesgNum::DeveloperDataId), {DeveloperDataId::ApplicationId, DeveloperDataId::DeveloperDataIndex});
    uint8_t appId[16] = {0x55,0x4e,0x41};
    w.data(1).bytes(appId, sizeof(appId)).u8(0).write();
    w.defineMessage(2, fit::mesgNum(fit::MesgNum::FieldDescription),
        {FieldDescription::DeveloperDataIndex, FieldDescription::FieldDefinitionNumber, FieldDescription::FitBaseTypeId,
         {FieldDescription::kFieldNameNum, fit::BaseType::String, 14}, {FieldDescription::kUnitsNum, fit::BaseType::String, 2}});
    w.data(2).u8(0).u8(0).u8(fit::baseTypeId(fit::BaseType::UInt8)).str("battery_level", 14).str("%", 2).write();
    w.defineMessage(3, fit::mesgNum(fit::MesgNum::Event), {Event::Timestamp, Event::EventField, Event::EventType});
    w.data(3).u32(t0).u8((uint8_t)fit::Event::Timer).u8((uint8_t)fit::EventType::Start).write();
    w.defineMessage(4, fit::mesgNum(fit::MesgNum::Record),
        {Record::Timestamp, Record::PositionLat, Record::PositionLong, Record::EnhancedAltitude, Record::HeartRate, Record::Cadence, Record::EnhancedSpeed},
        {{0, 1, 0}});
    const int N = 1500;
    double lat = 51.0890, lon = -115.3960;  // Canmore Nordic Centre trails
    for (int i = 0; i < N; ++i) {
        uint32_t ts = t0 + i + (i >= 900 ? 120 : 0);  // 2-minute pause at i=900
        if (i == 900) {
            w.data(3).u32(t0 + 900).u8((uint8_t)fit::Event::Timer).u8((uint8_t)fit::EventType::Stop).write();
            w.data(3).u32(t0 + 1020).u8((uint8_t)fit::Event::Timer).u8((uint8_t)fit::EventType::Start).write();
        }
        double a = i / 180.0;
        lat += 0.000030 * std::cos(a) ; lon += 0.000045 * std::sin(a * 0.7) + 0.00001;
        double alt = 1360 + 80 * std::sin(i / 300.0) + 15 * std::sin(i / 37.0);
        auto sc = [](double d) { return (int32_t)std::lround(d * 2147483648.0 / 180.0); };
        w.data(4).u32(ts).i32(sc(lat)).i32(sc(lon)).u32((uint32_t)std::lround((alt + 500) * 5))
            .u8((uint8_t)(135 + 20 * std::sin(i / 200.0))).u8(78).u32(4200u + (uint32_t)(1500 * std::sin(i / 90.0)))
            .u8((uint8_t)(90 - i / 100)).write();
    }
    uint32_t tEnd = t0 + N - 1 + 120;
    w.data(3).u32(tEnd).u8((uint8_t)fit::Event::Timer).u8((uint8_t)fit::EventType::Stop).write();
    w.defineMessage(5, fit::mesgNum(fit::MesgNum::Lap), {Lap::MessageIndex, Lap::Timestamp, Lap::StartTime, Lap::TotalElapsedTime, Lap::TotalDistance, Lap::AvgHeartRate});
    w.data(5).u16(0).u32(tEnd).u32(t0).u32((N + 119) * 1000u).u32(650000u).u8(140).write();
    w.defineMessage(6, fit::mesgNum(fit::MesgNum::Session), {Session::MessageIndex, Session::Timestamp, Session::StartTime, Session::Sport, Session::SubSport, Session::TotalDistance, Session::NumLaps, Session::AvgHeartRate});
    w.data(6).u16(0).u32(tEnd).u32(t0).u8((uint8_t)fit::Sport::Cycling).u8(8 /* mountain */).u32(650000u).u16(1).u8(140).write();
    w.defineMessage(7, fit::mesgNum(fit::MesgNum::Activity), {Activity::Timestamp, Activity::TotalTimerTime, Activity::NumSessions, Activity::Type, Activity::LocalTimestamp});
    w.data(7).u32(tEnd).u32(N * 1000u).u16(1).u8((uint8_t)fit::ActivityType::Manual).u32(tEnd - 6 * 3600).write();
    bool ok = w.finish() && w.ok();
    file->close();
    std::string s = fs.fileContents("una.fit");
    FILE* f = std::fopen("una-mtb.fit", "wb"); std::fwrite(s.data(), 1, s.size(), f); std::fclose(f);
    std::printf("ok=%d bytes=%zu\n", ok, s.size());
    return ok ? 0 : 1;
}
