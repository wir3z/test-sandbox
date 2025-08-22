# Owntracks
OwnTracks app connects your OwnTracks mobile app to Hubitat Elevation for virtual presence triggers

For discussion and more information, visit the Hubitat Community <a href="https://community.hubitat.com/t/release-owntracks/130821" target="_blank">[RELEASE] OwnTracks for Hubitat Presence Detection</a>.

The goal of the integration was to create a central management application for the OwnTracks app.  Mobile app settings and regions can be modified in the Hubitat app and then sent to the mobile app once they report a location.
The main app page contains a 'Member Status' table that displays location health of each member.  

The best experience, it is recommended to sign up for a free Google Maps API key to allow for dynamic region configuration and member maps.

## Hubitat Installation
### Automatic Install
Locate 'OwnTracks' in the Hubitat Package Manager to install the application and driver.
### Manual Install
1. Click 'Drivers Code' and the '+ New driver' button on the top right of the screen.
2. Paste the driver code into the screen, and click 'Save' in the top right of the screen.
3. Click 'Apps code' and the '+ New app' button on the top right of the screen.
4. Paste the app code into the screen, and click 'Save' on the top right of the screen.
5. Click the 'OAuth' button on the top right of the screen, leave all fields default and click 'Update'.

### First Time Opening the OwnTracks App
The OwnTracks app needs to be installed into Hubitat before the URL required for locations can be generated.  Click the 'Done' button, and then re-open the OwnTracks app to finish configuration.

## Quick Start Guide
To get presence detection working, three things need to be configured:
1. Install and configure the OwnTracks Mobile app to point to Hubitat
2. Configure a 'Home' region that will be used to determine if the phone is present or not present from the 'Configure Regions'/
3. Select the family member(s) to monitor their presence.
4. Open the Hubitat device for each mobile phone.  
	- The the 'Device Information' section, increase the 'Too many events alert threshold, per hour (100-2000)' to '1000'.
	- Click 'Save Device'.

The defaults in the app should give acceptable results without changes.  It is recommended to configure the sections in the 'Installation' portion of the app and test presence detection before changing additional settings.

Open the Hubitat OwnTracks app, and configure the the sections in 'Installation', below.
	
	
# Owntracks App
# Member Status
- Summary table for all users
	- Click the member name to directly access their device page.
	- If the member is disabled, it will be displayed with a strike through.
- Last Location Report
	- Date/time for the last update from a member.  If the member hasn't reporting after the set number of hours, it is displayed in red, with the number of hours since the last report.
- Last Location Fix
	- Date/time for the last location fix from a member.  If the member hasn't updated it's location after the set number of hours, it is displayed in red, with the number of hours since the last fix.
- The columns indicate the current pending requests for each member.  If 'Pending' is displayed, that user will get updated on the next location report.
- If region(s) are pending deletion, a banner will be displayed at the bottom of the table in red.
# Installation and Configuration
## Mobile App Configuration
This integration requires the Owntracks app to be installed on your mobile device:  https://owntracks.org/
NOTE:  If you reinstall the OwnTracks app on Hubitat, the host URL below will change, and the mobile devices will need to be updated.
	   This integration currently only supports one device per user for presence detection.  Linking more than one device will cause unreliable presence detection.

- Open the OwnTracks app on the mobile device, and configure the following fields.  Only the settings below need to be changed; leave the rest as defaults.
	### Android
	- Preferences -> Connection
		- Mode -> HTTP
		- Host -> Hubitat Cloud API link - click the 'Mobile App Installation Instructions' box in the Hubitat Owntracks App to get the link
		- Identification ->
			- Username -> Name of the user's phone that you would like to see on the maps (IE: 'Kevin')
			- Device ID -> Optional extra descriptor (IE: 'Phone').  If using OwnTracks recorder, it would be desirable to keep this device ID common across device changes, since it logs 'username/deviceID'.
		- Preferences -> Advanced
			- Remote commands -> Selected
			- Remote configuration -> Selected
	### iOS
	- Tap (i) top left, and select 'Settings'.
		- Mode -> HTTP
		- DeviceID -> 2-character user initials that will be displayed on your map (IE: 'KT').  If using OwnTracks recorder, it would be desirable to keep this device ID common across device changes, since it logs 'username/deviceID'.			
		- UserID -> Name of the user's phone that you would like to see on the maps (IE: 'Kevin')
		- URL -> Hubitat Cloud API link - click the 'Mobile App Installation Instructions' box in the Hubitat Owntracks App to get the link
		- cmd -> Selected
		
- Click the up arrow button in the top right of the map to trigger a 'Send Location Now' to register the device with the Hubitat App.  

## Configure Hubitat App - WiFi Settings, Location Performance, Geocode and Google Map API keys
### Hubitat Settings - WiFi, Units, Device Prefix
- Enter your 'Home' WiFi SSID(s), separated by commas (optional):
	- This will prevent devices from being 'non-present' if currently connected to these WiFi access points.
- SSID will only be used for presence detection when a member is within this radius from home
	- Once the member exceeds this distance, SSID will no longer be used for 'present' and 'not present' detection.
	
- Display Units
    - Select the slider to display all measurements in the Hubitat app in imperial units (mph, mi, ft) instead of metric units (kph, km, m)
    - All distances are stored in metric and may have minor rounding errors when converted to imperial for display.
    NOTE: The OwnTracks mobile app stores and displays all units in metric.
		
- Prefix to be added to each member's device name. 
    - By default, each member device will be created with a 'OwnTracks - ' prefix.
    - For example, member 'Bob' will have a device name of 'OwnTracks - Bob'. 
    - Enter a space to have no prefix in front of the member name.
	
### Geocode API Settings - Converts latitude/longitude to address
- Select the optional geocode provider for address lookups. Allows location latitude/longitude to be displayed as physical address.	
	- A geocode provider can be used to convert latitude/longitude into addresses and street addresses, as well as allow entering street address in the 'Add Region' section.
	- Three providers are supported: Google, Geoapify, and Opencage.  When a provider is selected, the link to sign up for an API key will changes.
	- Google provides the best accuracy in the forward and reverse lookups, but has the lowest quota.  NOTE: Google uses a monthly quota versus the daily quota of the other providers.
- Prevent geocode lookups once free quota has been exhausted.  Current usage: x/y per z	
	- Each provider has a free quota level.  This slider should be selected unless you are on a paid geocoder plan.
	- The usage total indicates the current usage in the quota period.  Google resets the total at the first of every month, the other providers daily, at 00:00 GMT.
	- Click the 'Sign up for a xxx API Key' link, and follow the directions to get the API key (this will be a string of random letters and numbers).
	- Once the quota has been exhausted for the period, the application will stop converting locations until the next quote period.
	- For Google, 'Geocoding API' must be enabled under 'API's & Services' (https://console.cloud.google.com/apis/dashboard)
- Geocode API key for address lookups:
	- Paste the API key provided into the box.
- All three providers can be configured, but only the select on will be used for geocode look ups.
- Select 'Disabled' to disable geocode lookups.

### Google Map API Settings - Creates a combined family map and adds radius bubbles on the 'Region' 'Add/Edit/Delete' page maps
- Creates a Google map with all members' thumbnails shown as well as region radius' when adding/editing regions.
	- Selecting a member will display an information window on their location and metrics.
	- If battery saver is turned on, the battery percentage is displayed in red.
	- If charging, the battery icon changes to a lightning bolt.
	- If wifi is disabled, the wifi symbol will have a line through it, and the member name is displayed in red
	- If the phone is using mobile data, the cellular signal icon is displayed in the top banner.  If using wifi data, a wifi symbol is displayed.
	- Distance from home, location accuracy and speed will be displayed if enabled.
	- The last location time will be displayed in red if it is stale.
    - NOTE:  Requires member thumbnails to be enabled, otherwise the selected glyph color is displayed.
	- If member history is enabled, the members past history will be displayed as fading dots.  Clicking a dot will give information about that location point.
- Prevent generating maps once free quota has been exhausted.  Current usage: x/y per month.	
	- Google maps as a free quota level.  This slider should be selected unless you are on a paid plan.
	- The usage total indicates the current usage in the quota period.  Google resets the total at the first of every month.
	- Click the 'Sign up for a Google API Key' link, and follow the directions to get the API key (this will be a string of random letters and numbers).
	- 'Maps JavaScript API' must be enabled under 'API's & Services' (https://console.cloud.google.com/apis/dashboard)
	- Once the quota has been exhausted for the period, the application will stop generating the map until the next quote period.
- Google Maps API key for combined family location map:
	- Paste the API key provided into the box.
- Map will only auto-zoom to fit members within this distance from home or a member.  
	- Entering 0 will allow the map to zoom out to fit all members.  Entering a distance will limit the amount of zoom.
	- If the member name is appended to the end of the Google Family Map URL, that member will be used for the auto-zoom distance check.
- Scale value for casting to 1024x600 or 1280x800 smart displays
	- Allows zoom on Google Nest Hub displays or Amazon Show displays.
- Enable to allow auto-zoom to zoom in closer when all member(s) are in the same area.
	- Enable to zoom to house level zoom.  Disable for neighborhood level zoom.
	
#### Member History and Pin Colors
- Enter the opacity value for the member accuracy radius
	- Creates a radius circle around the member scaled to the location accuracy.
	- Entering 0 disables the radius, the higher the number, the lest opaque the radius.
- Enter the total number of past member locations to save (includes the slower interval locations, below).  
	- Entering 0 will disable the member history.
- Time in minutes between adjacent history locations to denote an end of trip
- Remove previous history location if member is moving in the same direction
	- Select the slide to remove history points with similar bearings (value entered in the next setting).
	- Locations with bearings within this number of degrees are removed to reduce history size
- Scale value for the member details in the info boxes and map drawer.
	- Swiping up from the button of the Google Family Map will display the member information drawer.  This scaler changes the details size.
- Scale value for the past member locations 
	- Scales the history dots based on the value entered.
	- Scales the history lines/arrows based on the value entered.
- Distance between repeat arrows on the history lines
	- Enter the number of pixels between each directional arrow on the history lines.  The smaller the number, the closer the arrows are together.
	- Enter '0' to place a single arrow in the middle of each line.
- Save the last followed member between map reloads
	- When this slider is enabled, the the last followed member will be used on the map initial load or refresh.
	- When this slider is disabled, last followed member is reset and all users are displayed.
- Enable to display all member(s) history on map.  Disable to only display history of selected member on map.
	- When this slider is enabled, the past history dots will appear on the Google Map for ALL members all the time.  
	- When this slider is disabled, past history is only displayed when a member is clicked (selected).  Once you on the map to de-select the member, the history is hidden.
- Change the member pin color, as well as the glyph (center circle of the pin) colors.  The history markers on the map will have the same color as the glyph.
	- Enter an HTML color name (ie: MidnightBlue) (https://www.w3schools.com/tags/ref_colornames.asp)
	- or an HTML color code starting with # (ie: #191970) (https://www.w3schools.com/colors/colors_picker.asp)
	- Select each member from the drop box and change their respective glyph/history colors.  
	- NOTE:  If thumbnails are enabled, the image will replace the pin's glyph.

#### Region Pin Colors
- Change the region pin color, as well as the glyph (center circle of the pin) colors.
	- Enter an HTML color name (ie: MidnightBlue) (https://www.w3schools.com/tags/ref_colornames.asp)
	- or an HTML color code starting with # (ie: #191970) (https://www.w3schools.com/colors/colors_picker.asp)
	
## Configure Regions - Add, Edit, Delete, Assign 'Home'
NOTE:  If a Google Map API key is entered in the 'Configure Hubitat App -> Google map Settings', an interactive map will be displayed to allow regions to be added, edited, and deleted.  Regions will show radius bubbles and the home region can be directly assign to any pin.
- Select the 'Region Map Instructions and Delete Behavior' for information on how to use the interactive map.
- If a Google geocode API has been entered, an input box to allow direct address lookup will be displayed.
- If no Google Map API key has been entered, or the lookup quota has been exceeded, then the app will change to use the direct approach, below.

- Add Regions:
    - If a geocode provider has been configured in 'Configure Hubitat App', a text box will be displayed to allow entering street addresses to be converted to latitude/longitude.
		- NOTE: Google provides the best lookup accuracy.  If using other providers, confirm the map pin looks correct.
		- Click 'View larger Map' on the map and then right click on the map to get the correct latitude/longitude.
	- Enter the name, detection radius and coordinates.
	- Click the 'Save' button.
	- Click the 'Next' button to leave the screen, or abandon changes if 'Save' was not pressed.
- Edit Regions:
	- Select the region to edit.
	- Click the 'Save' button.
	- Click the 'Next' button to leave the screen, or abandon changes if 'Save' was not pressed.
	- NOTE: Changing the 'Region Name' will create a new region on iOS devices.  The previous named region will need to be manually delete from each device.
	- If the map pin is incorrect, click 'View larger Map' on the map and then right click on the map to get the correct latitude/longitude.
- Delete Regions:
    - If region(s) are pending deletion, a banner will be displayed at the bottom of the table in red.
	- Select the region to delete. 
	- NOTE:  The Play Store OwnTracks Android 2.4.12 does not delete regions, and requires them to be manually deleted from the mobile device.
	- Delete Region from Hub Only - Manually Delete Region from Mobile
		- Click the 'Delete Region from Hubitat ONLY' button.
		- On each mobile phone, find and delete the region selected above.
	- Automatically Delete Region from Hub and Mobile after Location Update
		- Selected region will be assigned an invalid lat/lon.
		- The region will remain in the list until ALL enabled users have sent a location report.
		- Once the last user has sent a location report, the region will be deleted from Hubitat.	- Click the 'Next' button to leave the screen, or abandon changes if 'Delete' was not pressed.

- Select your 'Home' place:
	- Select the region where 'Home' is for presence detection.  
		- The map will show the current pin location. 
	- If the list is empty select 'Add Regions', above, to create a home location.
	- When the member is within the region radius, they will be 'present'.  When they are outside this radius, they will be 'not present'.
		   
- Hubitat can retrieve regions from a member's OwnTracks mobile device and merge them into the Hubitat region list. 
	- If member(s) OwnTracks app already has configured regions, select member(s) that you wish to retrieve their regions when they report their location.
	- Those regions will be added to the Hubitat region list to be shared with other members.
	
- Send Region List to Secondary Hub
	- If a secondary hub is configured and enabled, this button is displayed.
	- Pressing the button will copy all regions from the primary hub to the secondary hub.

- Retrieve Region List from Secondary Hub
	- If a secondary hub is configured and enabled, this button is displayed.
	- Pressing the button will copy all regions from the secondary hub to the primary hub.

NOTE: A region named '+follow' is automatically created to allow iOS phones to have transition reporting.  	

## Configure Groups - Assign Friend Groups
- Groups are used to create isolated friend 'bubbles' to prevent locations being shared with all members.
### Example
	- There are two defined groups with 5 members:
		- Default: A, B, C, D
		- Group 1: B, E
	- 1. Members A, B, C, D will see each other, but not member E
	- 2. Member B will see members A, B, C, D and E
	- 3. Member E will only see members B and E
### How the app interacts with members in isolated friend groups:
	- Presence detection only occurs for members in the 'Default' group.
	- Regions are only shared with members in the 'Default' group.
	- Only members in the 'Default' group are sent to the OwnTracks Recorder.
	- Member locations are shared with other members in intersecting groups.
### How the Google Friend Map interacts with members in isolated friend groups:
	- By default, only members in the 'Default' group are displayed.
	- If a member name is passed in the '&member=' suffix of the URL, they will see all members in the groups that the member is assigned to.
	- In the above example, using the URL suffix of '&member=E' would only show members B and E on the Google map."

## Select Family Member(s) to Monitor
- Select family member(s):  
	- Once a mobile device has connected to the URL link from 'Mobile App Configuration', it will be populated in this list, but is disabled.  Select the user to enable presence detection.

## Select family member(s) to remain private. 
- Select family member(s):  
	- Locations and regions will NOT be shared with other members or the Recorder. 
	- Other member locations will not be shared with private members.
	- Only the 'Home' region will be pushed to private members.
	- Their Hubitat device will only display presence information.
	- If using the secondary hub connection, you will need to select these members as private on that hub.

## Configure Stale Members and Region Arrived/Departed Notifications
- Select device(s) to get notifications when members stop reporting locations and go stale.
	- Notification will be sent once-per day.
- Global enable/disable of notification devices.
	- Select notification devices that can be used to receive notifications.
	- Disabling a device will stop ALL member notifications, without changing each member, below.
	- Only devices that have been selected will appear in the per member notification settings, below.
- Select family member to change notifications.
	- 'Clear Settings' button
		- Removes all notification settings from the selected member.
	- Select device(s) to get notifications when this member enters a region.
		- (Only selected devices in the global settings, above are displayed.)
	- Trigger notifications when this member enters these region(s).
		- Selected regions trigger a notification when the member enters these region(s).
	- Select device(s) to get notifications when this member leaves a region.
		- (Only selected devices in the global settings, above are displayed.)
	- Trigger notifications when this member leaves these region(s).
		- Selected regions trigger a notification when the member leaves these region(s).
	- 'Save Settings' button
		- Saves all notification settings for the selected member.
	NOTE: The notifications can be checked by clicking the 'Arrived' and 'Departed' buttons in that member's Hubitat device.
- Add each new created region to all member notifications
	- If enabled, every newly created region will automatically be added to all members enter/leave notifications.
- Use a custom notification message
	- By default, the push notification is in the format 'NAME EVENT REGION at TIME'.  Enabled the slider to change the push notification.
	- The upper case variables are case sensitive:
		- NAME: member name
		- EVENT: enter or leave
		- REGION: place member name entered or left
		- TIME: time that member name entered or left region	
		

## Dashboard Web Links
- Displays direct cloud and local web links for the family, member and recorder maps.
- Selecting 'Disable cloud links' will prevent access to the web URL endpoints via the cloud links.
- If the member name is appended to the end of the Google Family Map URL, that member will be used for the auto-zoom distance check.


# Optional Features - Thumbnails, Recorder, Secondary Hub
## Enabling User Thumbnails:  
- Directions to create thumbnails for the mobile app and recorder.
- Display user thumbnails on the map.  Needs to have a 'user.jpg' image uploaded to the 'Settings->File Manager'
	- Maximum resolution 192x192 pixels 
	- Maximum file size of 45kB
	- Sends back each user's thumbnail picture when the send their location report.		

## Enable OwnTracks Recorder
- The optional Owntracks recorder:  https://owntracks.org/booklet/clients/recorder/ can be installed for local tracking.
- For the Recorder dashboard tiles and links to work outside the home network, the recorder must have a secure URL (https) and be secured with a public certificate.
- HTTP URL of the OwnTracks Recorder will be in the format 'http://enter.your.recorder.ip:8083', assuming using the default port of 8083.
- Follow the directions on the page 'Installing OwnTracks Recorder and Configuring User Card Instructions' to install OwnTrack Recorder and configure user cards.
- When 'Enable location updates to be sent to the Recorder URL' is selected, incoming mobile locations are mirrored to the above URL. 
- To view the OwnTracks recorder, open a web browser and navigate to 'http://enter.your.recorder.ip:8083'

## Enable Optional OwnTracks Frontend
- The optional Owntracks Fronend:  https://github.com/owntracks/frontend/ can be installed to provide a different user experience than the OwnTracks Recorder.
- NOTE:  OwnTracks Recorder MUST be already installed and operational.
- To view the OwnTracks frontend, open a web browser and navigate to 'http://enter.your.recorder.ip:8082'

## Link Secondary Hub
- Allows location updates to be sent to a secondary hub running the OwnTracks app.
- Enter the host URL of the Seconday Hub from the OwnTracks app 'Mobile App Installation Instructions' page.
- When 'Enable location updates to be sent to the secondary hub URL' is selected, incoming mobile locations are mirrored to the above URL. 


# Advanced Settings - Hub and Mobile
The default settings provide the best balance of accuracy/power.
NOTE: For settings to be sent to the device, 'Remote configuration' (Android) or 'cmd' (iOS) must be enabled in the mobile app.

## Hub App Settings	
The defaults for the rest of these settings should be sufficient for verifying operation.
- Restore Defaults
	- Resets the settings to the recommended defaults
- Enable high accuracy reporting when location is between region radius and this value:
	- When a user is between the home geofence radius and this radius, the app will switch the user to use high accuracy/high frequency reporting to ensure presence triggers operate correctly.
- High accuracy reporting is used for home region only when selected, all regions if not selected:
	- If selected, then high accuracy reporting only occurs in the geofence around home.  If deselected, high accuracy reporting will be used on all regions.  Note:  This will increase battery consumption.
- Highlight members on the 'Member Status' that have not reported a location for this many hours (1..168):
	- If a member's has not reported a location, or it's location position fix has been greater than this value, they will be highlighted in red on the table
- Display a warning in the logs if a family member reports a location but is not enabled:
	- If enabled, a warning will be disabled each time a member reports a location, but is not selected.
- Display a warning in the logs if a family member app settings are not configured for optimal operation:
	- If enabled, a warning will be displayed each time a user reports a location with non-optimal phone settings.
- Turn off high accuracy locations when the member is inside a region to reduce location jitter and power consumption. (Android ONLY)
	- If selected, high accuracy locations will be disabled when inside any region.
- Request a high accuracy location from members on their next location report after a ping/manual update to keep location fresh (Android ONLY):
	- If selected, members reporting stale locations will be requested to send a high accuracy location on their next location report
	
## Mobile App Location Settings	
- Restore Defaults
	- Resets the settings to the recommended defaults
- Using the defaults gives best balance of battery life and accuracy.  If 'Enable high accuracy reporting when location is between region radius and this value' was enabled on the main screen,
	the device will be switched to 'HIGH_POWER' mode with 'Request that the location provider updates no faster than the requested locater interval' when the mobile device is within that region.
- Location reporting mode
	- 'significant' - location updates are based on distance moved and time elapsed.  Best balance for battery consumption and performance.
	- 'move' - Continuously reports locations based on the configured interval.  Will result in much higher battery consumption.
- Device will send a location interval at this heart beat interval (minutes) (Android Only)
	- The mobile will report a location on intervals of this many minutes, regardless if it is moving
- Mobile App Location Performance (Android Only)	
    - Use GPS for high accuracy locations.  NOTE: This will consume slightly more battery but will offer better performance in areas with poor WiFi/Cell coverage. (Android ONLY)
    - Request a high accuracy location from members on their next location report after a ping update to keep location fresh. (Android ONLY)
- Do not send a location if the accuracy is greater than the given distance
	- Prevents the phone from returning locations if the accuracy is larger than the entered distance
- Number of days after which location updates from friends are assumed stale and removed
	- If a member stops reporting locations to your mobile phone, they will be marked as stale after this many days
- Request that the location provider deliver updates no faster than the requested locater interval
	- Reduces mobile data communications and battery consumption by throttling updates to the locater interval time
- How far the device travels before receiving another location update (Android Only)
	- If the phone has moved further than this distance in the alloted time, it will report a location
- Device will not report location updates faster than this interval (seconds) unless moving
	- Location updates will be reported on this interval unless the mobile is moving
- How often should locations be continuously sent from the device while in 'Move' mode (seconds) 
    - Interval for the mobile to report a location when in 'move' mode.  NOTE:  Faster the updates, the higher the battery consumption.
	
## Mobile App Display Settings	
- Restore Defaults
	- Resets the settings to the recommended defaults
- Replace the 'TID' (tracker ID) with 'username' for displaying a name on the map and recorder
	- Pushes the user name back as the tracker ID to allow the user's names to be shown on each device map.
- Notify about received events
- Include extended data in location reports
	- Returns extra information with each location report (BSSID, SSID, Battery Status, etc.)
- Allow the map to be rotated
- Display the region pins/bubbles on the map
- Show last reported location in ongoing notification banner
- Display Geocoder errors in the notification banner

	
# Maintenance - Sync Member Settings, Reset to Defaults, Deactivate and Delete Members
- Select family member(s) to update location, display and region settings on the next location update:
	- The user will be registered to receive this update once 'Done' is pressed.
- Recommended Default Settings
	NOTE:  Members, Regions, Recorder and Secondary Hub settings will not be deleted.
	- Restore Defaults for All Settings - resets the 'Hub App Settings', 'Mobile App Location Settings', and 'Mobile App Display Settings' to the recommended defaults.

	- Restore Defaults for 'Hub App Settings' to the recommended defaults.
	- Restore Defaults for 'Mobile App Location Settings' to the recommended defaults.
	- Restore Defaults for 'Mobile App Display Settings' to the recommended defaults.
- Delete or Deactivate Family Members:
	- Deactivates selected family members (clears the mobile app URL and waypoints to prevent calling home).
		- Only members that are not enabled will appear in the list.
		- Enabling a member will clear the deactivation.
		- Once selected, deactivation will occur on the next location update from the selected member.
	- Deletes selected family members from the app and their corresponding child device.  Ensure no automations are dependent on their device before proceeding.

		
# Logging
- Enable Descriptive Text Logging:
	- Displays periodic messages in the log.  Recommended to be enabled.
- Enable Debug Logging:
	- Displays verbose debug messages in the log for troubleshooting.  Recommended to be disabled.
- Turn off debug logging after this many hours (1..24):
	- Disables debug logging after this many hours elapses

		
		
# Hubitat Driver Configuration
- Once a user has been enabled in the app, a device with the name 'Owntracks - USERNAME' will be created.
- Display extended location attributes:
	- Displays additional location attributes (battery status, altitude, accuracy, etc.)
- Create a HTML MemberLocation
	- Creates a 'MemberLocation' HTML attribute that can be added to the dashboards.  Displays the map location, information as well as presence.
- Change MemberLocation background color based on presence
	- Background for the map in the MemberLocation will be green for present, red for not present if enabled.  Otherwise the default background color is used.
- Create a HTML PastLocations tile	
	- Creates a 'PastLocations' HTML attribute that can be added to the dashboards.  Displays the the member's locations based on the selected start/end dates in the tile.  By default, it selects 0.5 days (12-hours) and can be configured with the setting below.  Requires Recorder to be configured.
- Display past locations history as lines instead of points
	- By default, the past location history is displayed as bread crumb points.  Select to display as string lines.
- PastLocations tile search window start date is this many days from current date (0.1..31)
	- Enter the number of days the 'PastLocations' tile will start searching from the current date.
- Create a notification if member enters a region
	- Sends Hubitat app notifications when the member arrives
	NOTE:  Requires a notification device to be selected in the 'Configure Region Arrived/Departed Notifications' -> 'Select notification devices to get region enter/leave notifications."
- Create a notification if member leaves a region
	- Sends Hubitat app notifications when the member leaves
	NOTE:  Requires a notification device to be selected in the 'Configure Region Arrived/Departed Notifications' -> 'Select notification devices to get region enter/leave notifications."
- Configure the number of seconds the member needs to be in the same region before the new transition update is sent.
	- This is used to address arrived/departed ping-pong messages.
- Enable Description Text logging
	- Displays general descriptive logs (arrived, departed, etc.)
- Enable Debug Logging
	- Displays more verbose diagnostic logging.  Recommended to be off.
- Enable Logging of location changes:
	- Logs an entry when a change in user location occurs

- Main Attribute Description
	- BSSID : MAC address of the WiFi connected WiFi access point
	- SSID : SSID of the WiFi connected WiFi access point
	- accuracy : accuracy of the location
	- altitude : altitude of the location
	- battery : battery %
	- batteryStatus : Unknown/Unplugged/Charging/Full
	- dataConnection : WiFi/Mobile
	- distanceFromHome : distance location is from the home coordinates
	- imageURL : URL for the members image card
	- lastLocationtime : timestamp for the last location - this updates on each incoming location
	- lat : latitude of the location
	- location : region the user is currently in, or the lat/lon if outside a region
	- lon : longitude of the location
	- monitoringMode : Significant/Move
	- presence : present/not present
	- PresenceTile : HTML presence tile
	- since : timestamp on the last location change - only updates if the location has moved
	- sourceTopic : user/device information
	- transitionRegion : region the user arrived/departed
	- transitionDirection : direction of travel from the region (arrived/departed)
	- transitionTime : time the user arrived/departed the region
	- triggerSource : Ping/Region/Report Location/Manual/Beacon/Timer/Monitoring/Location
	- verticalAccuracy : vertical accuracy
	- address : Full address of the location (if available) or lat,lon
	- streetAddress : Street address of the location (if available) or lat,lon
	- MemberLocation : HTML status tile
- Additional Attribute Description for APK 2.4.17.  
	- wifi : on/off
	- batterySaver : 0/1, 1 indicates the phone is in battery saver mode
	- hiberateAllowed : 0/1, 1 indicates the OwnTracks app can pause if unused for a period of time
	- batteryOptimizations : 0/1, 1 indicates that the OwnTracks app has battery optimizations on which will impact performance
	- locationPermissions : 0/1, 1 indicates the location permission settings are configured in a way that will impact performance

# Hubitat Common Driver Configuration
- Create a HTML FriendsLocation tile	
	- Creates a 'FriendsLocation' HTML attribute that can be added to the dashboards.  Displays the the all member's current locations in a single map.  Requires Recorder to be configured.
	
# Hubitat Custom Dashboard Tiles
- Each user device creates the following custom attribute tiles that can be displayed on a dashboard:
## Each user device
	- PresenceTile : Designed to be a 1x1 simple tile with location at the top, with the member thumbnail (if available), and their presence status.)
	- MemberLocation  : If enabled in user device, shows the user on an interactive Google map with other metrics.
	- PastLocations : If enabled in user device, and requires the OwnTracks Recorder to be configured, shows the user's locations over the past 12-hours on an interactive map.
	
## OwnTracks user device
	- RecorderFriendsLocation : If enabled in user device, and requires the OwnTracks Recorder to be configured, shows all user's current locations on an interactive map.
	- GoogleFriendsLocation : If configured in the OwnTracks app, shows all user's current locations on an interactive map.
	- The 'push' momentary device will refresh the above two maps when pressed or when triggered from a dashboard.

## Using Dashboard Tiles
	- Create a dashboard.
	- 'Pick a Device': Select the user device.
	- 'Pick a Template': 'Attribute'
	- 'Options -> Pick and Attribute'.  Select one of the tile names, above.
	- Select the height/width from the arrow boxes at the top of the screen.  'PresenceTile' is made for a 1x1.  The other can be 2x4 to fit full screen on a mobile app.

	
# FAQ (Frequently Asked Questions)
## What is OwnTracks?
OwnTracks is an open source mobile presence application for the Android and iOS platforms.  Unlike other commercial presence apps, OwnTracks only sends location information to a location that you managed, such as this integration, and does not monetize your location information.

### Where do I get the Mobile application?
You can get the official released version for Android and iOS here: https://owntracks.org/ .  A modified Android version with multiple improvements including background operation, remote region delete and keep alive can be found here:  https://github.com/wir3z/hubitat/blob/main/owntracks-android-apk/app-gms-debug_v2.4.17.apk

### How do I set up Hubitat to support this integration?
View the README.MD found here: https://github.com/wir3z/hubitat/tree/main/owntracks-hubitat
Follow the 'Hubitat Installation', 'Quick Start Guide', and complete the steps in the 'Installation' section.  The app needs minimal configuration to get you operational and the defaults should be sufficient for most applications.

### What if I need help?
Visit the Hubitat Community thread '[RELEASE] OwnTracks for Hubitat Presence Detection' (https://community.hubitat.com/t/release-owntracks/130821) to ask questions and get community support.

### How does this setup work?
The mobile application ONLY communicates with the Hubitat app when a location report needs to be sent from the mobile device.  When the Hubitat app receives the location report, it can respond with configuration or region updates.

There is no ability for the Hubitat app to push information to the OwnTracks mobile app, nor can it query it in real time.  All communications are initiated by the mobile app.

## Display Units
### Why do a see metric units in the mobile app after I set the the Hubitat app to 'imperial' units?
The 'metric'/'imperial' selection only applies to the Hubitat app and is not shared with the mobile app.

### I have the Hubitat app set to 'Imperial' but the distances are slightly off
The Hubitat app processes all the distances and speeds in metric.  They are converted to/from 'imperial' for display purposes only and may have slight rounding errors.

## Regions
### What are regions?  Why do I want these?
Regions are 'location bubbles' that are bound by a radius around it.  If the mobile enters or leaves one of these radius', the Hubitat app can display the status of this transition.

They are handy for giving more meaningful locations of 'Arrived at Work' or 'Departed from Home'.  You need to create at least one region to define your home location for presence detection to work.

### I've added, edited or deleted a region, and the mobile app isn't showing the changes
The mobile will retrieve the update on its next location report.  You an trigger a manual location update from the mobile app by tapping the up arrow in the top right of the map to force this to occur instantly.

### I've deleted regions from the Hubitat app, but they are not being deleted from my phone
The Android OwnTracks Play Store v2.4.12 has a bug that prevents deleting regions.  Regions need to be manually deleted from each mobile device.

iOS devices use the 'Region Name' as a unique identifier.  If the name was changed on the hub vs the mobile or vice versa, they they become unlinked and would need to be manually deleted from the mobile device.

### I've edited the name of a region in the Hubitat app, now my iOS device is showing the old and new one
iOS devices use the 'Region Name' as a unique identifier.  Changing the name creates a 'new region' and the old one would need to be manually deleted from each mobile device.

### I see a region called '+60follow' or some other '+follow' name.  What do these do?
In order to ensure iOS devices have better performance and stop sleeping the OwnTracks app, one of these regions must exist on the phone.

The number after the + sign is the 'locater interval' value from the Hubitat 'Advanced Mobile App Settings'.  By default it is '60' seconds.  If you change the 'locater interval' value in 'Advanced Mobile App Settings', your iOS device will receive a new +follow region.  Delete the old one otherwise performance will be impacted.

## Presence Detection
### My phone isn't reporting to the Hubitat App
1. Make sure that the app has location permissions enabled.  Without location permissions, the app is not allowed to report locations.
2. If thumbnails are enabled, ensure the size is not too large.  Thumbnails can be up to 192x192 pixels, but high DPI images will result in large file sizes.  For optimal performance, create a thumbnail of 96x96 pixels and 96 DPI.  This will create a final file size of ~5kB.

### My iOS phone is slow to update
Check the regions on the impacted device.  There should be only one +follow named region.  If there are multiple, delete all of the ones that do not match the 'locater interval' number from the Hubitat 'Advanced Mobile App Settings'.  By default, you should have a '+60follow' region.

### My iOS or Android phone is slow to update
By default, the locater service in the mobile optimizes power and performance by trying to get a location from these three sources, in this order:
1. Cellular
2. WiFi (listens for nearby WiFi access points, and does a lookup for a location)
3. GPS (only when explicitly selected, or moving at higher speeds due to high battery consumption)

If WiFi has been disabled on your mobile device, location updates will be negatively impacted and may not reflect your current location.  For best performance, leave WiFi on.

If you have an Android phone, and are in an area with little to no WiFi access points, enable the 'Use GPS' slider in 'Advanced Mobile App Settings'.

### When I look at where my locations have been, they seem to be miss my actual route or not give a location at all
When using WiFi for locations, the phone listens to the nearest broadcasting source.  If traveling at a lower rate of speed (walking, cycling) the locations will ping-pong from the closest WiFi source.  When driving, the mobile tends to switch to GPS to get a more accurate location.

If a more accurate location is needed, enable the 'Use GPS' slider in 'Advanced Mobile App Settings'.  Locations will be more precise but will impact battery performance.

## OwnTracks Recorder
### What is Recorder, and why do I want this?
Recorder captures all the locations send to the Hubitat app to allow a breadcrumb trail to be viewed on a map at a later time.  If you have a need to see where a member has been, this is a handy tool, but is not needed for the Hubitat presence detection.	

## OwnTracks Frontend
### What is Frontend, and why do I want this?
Frontend requires the Recorder to be installed an configured.  It offers a different user experience for browsing past breadcrumb trails to be viewed on a map.	

## Map and Geocode API Keys
### What are these keys for, and why do I want this?
The app supports using a Google Maps API key to generate dynamic member and region configuration maps.  For the best experience, sign up for a free Google Map API key using the directions on the 'Configure Hubitat App' page.

The app supports using a Google Map, Geoapify and Opencage API keys for converting latitude and longitude to street addresses.  Directions to sign up for free API keys are found on the 'Configure Hubitat App' page.