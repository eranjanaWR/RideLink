# RideLink Postman demo

1. Start the backend with `./run-local.sh` and wait for all four services.
2. Import `RideLink.postman_collection.json` into Postman.
3. Import `RideLink.postman_environment.json`.
4. Select the **RideLink Local** environment.
5. Set `demoPassword` locally to a non-production demonstration password. Do not export the populated environment.
6. Run requests in folder order, or use **07 - End-to-End Demo** for the presentation.

The collection initializes unique emails, phone numbers, service area, licence number, and registration number when `runSuffix` is empty. Before a fresh run, clear `runSuffix` and all generated IDs/tokens. Registration, login, driver, vehicle, ride, estimate, and payment responses automatically populate the relevant environment variables.

The collection never requires `JWT_SECRET`, `INTERNAL_SERVICE_KEY`, or a MongoDB URI. Final fare and payment creation occur through Ride's `complete-with-payment` endpoint. Direct final-fare and payment-create endpoints are documented in their folder descriptions because they require a legitimate ADMIN token or trusted internal service authentication.

For screenshots, use the checklist in `../docs/POSTMAN_SCREENSHOT_CHECKLIST.md` and crop all credentials and token values.
