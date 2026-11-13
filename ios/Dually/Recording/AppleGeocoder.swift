import CoreLocation
import DuallyShared
import Foundation
import MapKit

// Asks Apple for the address of a trip's start or end, for the address lookup the iPhone shares
// with Android (TripAddresses): which trips are due, the retries, and the line a trip shows
// ("3104 97 St NW, Edmonton") are all the shared code's. On iOS 26 and later this goes through
// MapKit's reverse-geocoding request, which replaced Core Location's geocoder; before that,
// through Core Location's. The position leaves the phone here, and nowhere else in Dually.
final class AppleGeocoder: ApplePlaceFinder {
    func find(latitude: Double, longitude: Double, reply: PlaceReply) {
        let location = CLLocation(latitude: latitude, longitude: longitude)
        Task { @MainActor in
            do {
                reply.places(candidates: try await Self.places(at: location))
            } catch {
                if Self.knowsNoAddress(error) {
                    // A spot with no address: an answer, not a failure.
                    reply.places(candidates: [])
                } else {
                    let failure = error as NSError
                    reply.failed(reason: "\(failure.domain) error \(failure.code)")
                }
            }
        }
    }

    @MainActor
    private static func places(at location: CLLocation) async throws -> [PlaceParts] {
        if #available(iOS 26, *) {
            guard let request = MKReverseGeocodingRequest(location: location) else { return [] }
            // MapKit gives no street or house number apart. Its short address is the street
            // address with the town ("11455 87 Ave NW, Edmonton"), the same line as Android's,
            // even where its name is a place's own ("University of Alberta"); so the street is
            // the short address without the town. Its full address is the fallback.
            return try await request.mapItems.map { item in
                let town = item.addressRepresentations?.cityName
                return PlaceParts(
                    houseNumber: nil,
                    street: Self.street(in: item.address?.shortAddress, town: town),
                    town: town,
                    district: nil,
                    county: nil,
                    region: nil,
                    firstLine: item.address?.fullAddress,
                    country: item.addressRepresentations?.regionName,
                    countryCode: nil,
                    postalCode: nil
                )
            }
        } else {
            return try await CLGeocoder().reverseGeocodeLocation(location).map { mark in
                PlaceParts(
                    houseNumber: mark.subThoroughfare,
                    street: mark.thoroughfare,
                    town: mark.locality,
                    district: mark.subLocality,
                    county: mark.subAdministrativeArea,
                    region: mark.administrativeArea,
                    firstLine: mark.name,
                    country: mark.country,
                    countryCode: mark.isoCountryCode,
                    postalCode: mark.postalCode
                )
            }
        }
    }

    /// "11455 87 Ave NW" from "11455 87 Ave NW, Edmonton"; nil when nothing but the town is left.
    /// Runs of spaces are closed up first: MapKit can write "Labatt Breweries Alberta,  Edmonton".
    private static func street(in shortAddress: String?, town: String?) -> String? {
        guard let shortAddress else { return nil }
        let line = shortAddress.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard let town else { return line }
        if line == town { return nil }
        let suffix = ", " + town
        return line.hasSuffix(suffix) ? String(line.dropLast(suffix.count)) : line
    }

    private static func knowsNoAddress(_ error: Error) -> Bool {
        let failure = error as NSError
        return (failure.domain == kCLErrorDomain && failure.code == CLError.geocodeFoundNoResult.rawValue)
            || (failure.domain == MKErrorDomain && failure.code == Int(MKError.placemarkNotFound.rawValue))
    }
}
