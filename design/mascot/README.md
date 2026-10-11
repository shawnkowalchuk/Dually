# The mascot

MilO's mascot: a green block with a speedometer and a rev counter for eyes, the app's "M" for a mouth, hose arms and legs, white gloves and green shoes. Built in Blender 5.2 on 2026-10-09 from the picture in `reference.webp`. It is a 3D model here. The app carries no model: it shows six pictures of it, two for each of its greetings and two for its one-year party, drawn here once (`docs/adr/ADR-007-mascot-animated-picture.md`). The website shows three more, drawn the same way.

Nothing in this folder is part of the app's build. The nine pictures the app carries are written from here into `app/src/main/res/drawable-nodpi/`, the website's three into `website/mascot/`, and all twelve are committed.

| File | What it is |
|---|---|
| `milo_mascot.blend` | The Blender file: model, skeleton, clips, and a small studio (camera, lights, white backdrop) |
| `scripts/build_milo.py` | Builds the model and its skeleton from nothing |
| `scripts/studio_milo.py` | Builds the camera, the lights and the backdrop, and stands the reference picture beside the model |
| `scripts/animate_milo.py` | Writes the clips |
| `scripts/export_milo.py` | Writes the `.glb` file and prints what is in it |
| `scripts/render_greeting.py` | The camera, the stage and the halo, which every script that draws a picture reads from it. Run by itself it draws the frames of the wave the app had until 2026-10-11: the walk from the right, and the turn and wave |
| `scripts/pack_greeting.py` | What the packing scripts share: where the app's pictures go, a frame's time, how a folder of frames becomes an animated picture. Until 2026-10-11 it packed that wave's two pictures; it packs nothing by itself now |
| `scripts/render_clips.py` | Draws the frames of any clip, at any size |
| `scripts/render_dials.py` | Draws the two dials without their needles, and the needles' blades alone, for the website |
| `scripts/render_lights.py` | Draws the brake lights to be shown: his back with them off and lit, and a turn-around |
| `scripts/pack_clips.py` | Packs those frames into the pictures in `renders/`, and into the website's three |
| `scripts/render_moonwalk.py` | Draws the frames of the app's second greeting, the moonwalk: the glide, and the middle part (below) |
| `scripts/pack_moonwalk.py` | Packs those frames into the moonwalk's two pictures. Plain Python, not run in Blender |
| `scripts/preview_moonwalk.py` | Lays the same frames over a picture of Home and writes a film of it: the preview Shawn was shown. Plain Python, not run in Blender |
| `scripts/render_party.py` | Draws the frames of the party: with its confetti, without it, or the confetti alone (below) |
| `scripts/preview_party.py` | Makes a film of those frames on a phone's screen, with sound if it is given any: the preview Shawn was shown. Plain Python, not run in Blender |
| `scripts/pack_party.py` | Packs those frames into the party's two pictures. Plain Python, not run in Blender |
| `scripts/render_run_slide.py` | Draws the frames of the run and the slide: with their smoke, without it, or the smoke alone, for the preview of them and, 375 pixels high, for the app's three pictures (below) |
| `scripts/pack_run_slide.py` | Packs those frames into the three pictures the app shows when a report has been sent: a sheet of the run in, and two animated pictures that stand still on the screen. Plain Python, not run in Blender |
| `scripts/preview_run_slide.py` | Makes a film of those frames on a phone's screen, moving him across it, with sound if it is given any. Plain Python, not run in Blender |
| `scripts/check_run_slide.py` | Says in which frames of the run and the slide a shoe or a hand goes into his body, a dial, a shoe or the other hand |
| `scripts/render_wave2.py` | Draws the frames of the app's first greeting, the wave, as it is since 2026-10-11: the walk in, the stop, the wave and the turn away, and the walk off (below). It draws the brake lights both ways, as the two previews had them |
| `scripts/preview_wave2.py` | Lays those frames over a picture of Home and writes two films of it, one for each way with the lights: the previews Shawn was shown. Plain Python, not run in Blender |
| `scripts/pack_wave2.py` | Packs those frames into the wave's two pictures. Plain Python, not run in Blender |
| `scripts/check_wave2.py` | Says in which of those frames a shoe or a hand goes into his body, a dial, a shoe or the other hand |
| `export/milo_mascot.glb` | The model with every clip, without the hat, the party cap, the popper, the confetti and the smoke (3.47 MB), for whatever comes later. Nothing uses it today |
| `renders/milo_thumbs_up.png` | The mascot as Blender draws it, in the pose of the reference picture |
| `renders/milo_flex.webp`, `milo_peace.webp`, `milo_rock_on.webp` | Three clips as animated pictures with a see-through background, for whatever comes later (below) |
| `renders/milo_flex.png`, `milo_peace.png`, `milo_rock_on.png` | A still of the pose each of them holds |
| `renders/milo_back.png`, `milo_back_lit.png`, `milo_back_three_quarter_lit.png` | His back with the brake lights off and lit, and from behind and to one side with them lit, on a see-through background (below) |
| `renders/milo_back_on_dark.png`, `milo_back_lit_on_dark.png`, `milo_back_three_quarter_lit_on_dark.png` | The same three on the app's dark page, with the halo round the lit lights |
| `renders/milo_turn_around_on_dark.webp` | A turn-around on the dark page, the lights coming on while his back is to the viewer: an animated picture that starts again for ever |
| `renders/milo_party_cap.png`, `milo_party_cap_three_quarter.png` | Him in his party cap, from the front and three quarters on, on a see-through background (below) |
| `renders/milo_one_year.webp`, `milo_one_year.png` | The party, confetti and all, as an animated picture with a see-through background, and a still of him cheering under the confetti |
| `renders/milo_run.webp`, `milo_slide.png` | One cycle of the run, on the spot, as an animated picture that starts again for ever, and a still of the slide with the brake lights lit, both with a see-through background (below) |
| `renders/milo_slide_on_dark.png`, `milo_run_slide_on_dark.webp` | The slide on the app's dark page, with its smoke and the halo, and the whole preview at half its size as an animated picture that starts again for ever |
| `renders/milo_walk_in.webp`, `milo_walk_off.webp` | One cycle of the walk in and one of the walk off, on the spot, as animated pictures that start again for ever, with a see-through background (below) |
| `renders/milo_walk_off_lit.png`, `milo_walk_off_lit_on_dark.png` | A still of him walking off with the brake lights lit: on a see-through background without a halo, and on the app's dark page with it |
| `renders/milo_wave2_a_on_home.webp`, `milo_wave2_b_on_home.webp` | The two previews of the wave, walked through, small: the foot of a phone's screen, Home behind him, as animated pictures that start again for ever |
| `reference.webp` | The picture the mascot was built from |

**The scripts are the source, the `.blend` is their result.** To change the mascot, change a script and run the first four again, in the order above, rather than editing the Blender file by hand: what is done by hand is gone at the next run. Each of them only rebuilds its own collections (`MilO_Mascot`, `MilO_Confetti` and `MilO_Smoke`; `MilO_Studio`; `MilO_Reference`). After a change to the model, or to a clip that a picture is drawn from, that picture has to be drawn again too (below).

Without Blender's window, which is how it was last run (2026-10-09). `-b` is "no window", and the last part saves the file, which nothing else does:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/build_milo.py --python design/mascot/scripts/studio_milo.py --python design/mascot/scripts/animate_milo.py --python design/mascot/scripts/export_milo.py --python-expr "import bpy; bpy.ops.wm.save_mainfile(compress=True)"
```

It takes a few seconds. Blender keeps the file it replaced beside the new one as `milo_mascot.blend1`; git ignores it, and it can be deleted. On this Mac `blender` is `/Applications/Blender.app/Contents/MacOS/Blender`.

The same command without `-b` and without its last part opens Blender's window and leaves the file to be saved by hand. The scripts that draw pictures are run differently and are not part of it.

**Run twice, it writes the same clips and the same `.glb`, byte for byte.** That is the check that a change to a script left the other clips alone: build once from the scripts as they were and once from the changed ones, and compare. The Blender file that was committed before the evening of 2026-10-09 was not quite what its scripts write: about 850 of its 29,600 key values were off by less than a millionth, the mark of a file built in several sittings. Nothing that can be seen.

## The clips

Each is one action, at 24 frames a second, starting on frame 0. Every clip but the loops, `WaveUp`, the moonwalk's four, `OneYear`, the five of the run and the four of the walk in and off begins and ends in the same standing pose, so one can follow another. `OneYear` begins in it, in his party cap, and ends with both arms up. The five of the run follow one another and he never stands in them. The four of the walk in and off go round `Wave`: `WalkStop` ends in the standing pose and `TurnAway` begins in it. The needles move in every clip.

`Flex`, `Peace` and `RockOn` were added on 2026-10-09 for pictures to come ("future content ideas or use on the website"); nothing plays them yet. `WaveUp` and `WaveLoop` were added on 2026-10-10 for the website, where he waves for as long as a pointer is on him. The four clips of the moonwalk were added the same day for a second greeting Shawn asked for; the app plays them since then, in turn with the wave (see "The hat and the moonwalk"). `OneYear` was added the same day for the day the app has been used for a year, with the confetti's own clip beside it; the app plays it since then, on that day and on demand (see "The party"). `Run`, `SpinUp`, `Burnout`, `BrakeSlide` and `Slide` were added the same day for an animation Shawn asked for without a place for it yet, with the smoke's own clip beside them; **they are a preview, and nothing plays them yet** (see "The run and the slide"). `WalkIn`, `WalkStop`, `TurnAway` and `WalkOff` were added on 2026-10-11, when Shawn asked for the wave again with the brake lights in it, in at the left and out at the right; the app plays them since then, with `Wave` between the second and the third, as its first greeting (see "The wave, walked through").

| Clip | Length | What happens |
|---|---|---|
| `Idle` | 4 s, loops | Stands, breathes, sways a little |
| `Wave` | 2 s | The right arm goes up, the open hand waves |
| `ThumbsUp` | 2 s | The pose of the reference picture, and back |
| `Walk` | 1 s, loops | Walks on the spot. The wave the app had until 2026-10-11 played it at twice this speed; nothing plays it now |
| `Celebrate` | 3 s | Crouches, jumps twice with both arms up |
| `Flex` | 4.5 s | Shows off in four poses and squeezes each: both fists up beside the head; the right arm up and the left down; the other way round; both again. The RPM needle jumps with every squeeze |
| `Peace` | 2.75 s | The left hand goes up beside the head and holds the peace sign for a second and a half: two fingers up and apart, the third curled under the thumb, the palm to the viewer |
| `RockOn` | 2.75 s | The right hand goes up and holds the horns: the two outer fingers up, the middle one curled under the thumb. Three nods to a beat, the RPM needle at the red line with each |
| `WaveUp` | 0.5 s | The right arm goes up and stays up: its last frame is `WaveLoop`'s first. The way into the loop, not a clip to play alone; nothing brings the arm down again but `Wave`'s own ending |
| `WaveLoop` | 0.5 s, loops | The arm held up, one wave of the hand. It is `Wave`'s wave at two a second instead of two and a half, so that one wave is 12 frames and the loop closes on a frame |
| `Moonwalk` | 0.58 s, loops | In his hat, glides backward on the spot: one foot up on its toe, which stays where it is on the floor, while the other slides back flat; then they change over. One cycle, 14 frames, carries him 1.12 units |
| `MoonwalkStop` | 1.83 s | From `Moonwalk`'s first frame. He brakes: his back end skids round towards the viewer and the brake lights come on. Then he comes back round into the pose, the right hand to the brim of his hat, the left at his belt, the right knee popped in, and thrusts his hips three times, the RPM needle jumping with each |
| `KickSpin` | 1.08 s | From that pose. The left leg kicks out to its side, then he turns once round on the spot in half a second, the brake lights going past the viewer, and comes down on the toes of both feet; the brake lights go out |
| `MoonwalkOn` | 0.42 s | From his toes back into `Moonwalk`'s first frame: the hand comes down from the hat and he turns back the way he glides |
| `OneYear` | 5.42 s | In his party cap. He stands still for half a second, and bobs up, glad. His left hand goes behind his back, feels about, and comes out with a party popper, which he holds up beside his head. He brings it in front of his chest, its mouth up between the dials; his right hand takes the string and draws it tight, the revs climbing; he pulls, and on frame 73, 3.04 seconds in, it goes off: both needles right round, a jump, and he cheers with both arms up, the popper in his left hand, while the confetti comes down |
| `OneYear_Confetti` | 5.42 s | Not his: the confetti's own clip, on the confetti's own skeleton (`MilO_Confetti_Rig`). Nothing until frame 73; then a flash at the popper's mouth for three frames, and the paper and the streamers up over his head and down round him |
| `Run` | 0.33 s, loops | Runs, on the spot as `Walk` walks: leaning the way he goes, fists pumping, each foot on the floor for three frames of the eight and both off it between. One cycle, 8 frames, carries him 1.8 units |
| `SpinUp` | 0.25 s | From `Run`'s first frame into `Burnout`'s. His feet lose their grip: his legs go faster, the floor stops going by, and the top of him is thrown forward and comes back |
| `Burnout` | 0.25 s, loops | Runs on the spot, 6 frames to a cycle: leaning further, his shoes going round like wheels and scuffing back along the floor on their toes. Both needles at the top |
| `BrakeSlide` | 0.92 s | From `Burnout`'s first frame. His feet bite: he crouches back for four frames and bolts for five, both arms thrown back. On frame 9, 0.375 seconds in, he brakes: both feet go down ahead of him, the brake lights come on at once, his back end swings round towards the viewer, 80 degrees and a little too far, and he leans back against the slide, his hands flung out and open. Both needles fall |
| `Slide` | 0.33 s, loops | The slide, held: its first frame is `BrakeSlide`'s last. The brake lights lit, a shudder every four frames |
| `WalkIn` | 0.5 s, loops | Walks, on the spot as `Walk` walks, with a longer stride: one cycle, 12 frames, carries him 1.04 units. Turned 58 degrees to his left of facing the viewer, and going level across the picture, which is 38.5 degrees to his left of straight ahead. Its first frame is the middle of a step: the left foot flat under him, the right in the air beside it |
| `WalkStop` | 0.46 s | From `WalkIn`'s first frame. The foot in the air comes down, the brake lights come on at once, and the top of him rocks on the way he was going. Then he crouches, hops, and turns to face the viewer in the air. It ends in the standing pose, the lights lit behind him: `Wave`'s first frame |
| `TurnAway` | 0.88 s | From the standing pose, `Wave`'s last frame. He crouches, the top of him already turning, hops, and turns 135 degrees to his left in the air: his back comes round to the viewer, the brake lights lit. He lands, stands for seven frames, and from frame 17 lifts his right foot for the first step while the lights go out. It ends in `WalkOff`'s first frame |
| `WalkOff` | 0.5 s, loops | The same walk, he turned 135 degrees to his left of facing the viewer: seen three quarters from behind, going the same way across the picture, which is now 38.5 degrees to his right of straight ahead. The brake lights unlit |
| `RunSlide_Smoke` | 5.25 s | Not his: the smoke's own clip, on the smoke's own skeleton (`MilO_Smoke_Rig`), for those five shown one after the other: a little dust at every footfall, puffs kicked back from the shoe that scuffs while he runs on the spot, a burst where he pushes off, and a trail from both shoes from the brake on |
| `Gauge_Speed` | 2 s | The speed needle alone, from 0 to 240 |
| `Gauge_RPM` | 2 s | The RPM needle alone, from 0 to 8 |

The two gauge clips move one bone each and nothing else, so they can be laid over any other clip and set to a live value: speed ÷ 240 of the way through the first, RPM ÷ 8 of the way through the second.

## The app's pictures

The app has two greetings, which take turns, and each is two pictures: a still sheet of frames the app steps through while it moves the mascot, and an animated picture Android plays while he stays where he is. All are WebP with a see-through background, 24 frames a second, 2.5 pixels to the dp. The wave's two are in "The wave, walked through", below, and the moonwalk's two in "The hat and the moonwalk". (The one-year party's two are of another kind, both animated and with fewer pixels to the dp: "The party", below.)

| Greeting | The sheet | The animated picture | Drawn by | Packed by |
|---|---|---|---|---|
| The wave | `mascot_walk_in_and_off.webp`, 24 frames: a cycle of the walk in and a cycle of the walk off | `mascot_stop_and_wave.webp`, 80 frames | `render_wave2.py` | `pack_wave2.py` |
| The moonwalk | `mascot_moonwalk.webp`, 14 frames: a cycle of the glide | `mascot_stop_and_spin.webp`, 80 frames | `render_moonwalk.py` | `pack_moonwalk.py` |

All four are in `app/src/main/res/drawable-nodpi/`, beside the party's two ("The party: a year of use") and the three of the run across the Report screen ("The run and the slide"). Each greeting is made in two steps. The first needs the Mac's Blender. The second needs Pillow (`pip3 install pillow`), and `cwebp` and `webpmux` (`brew install webp`).

- **The app's code has to be told what the second step prints:** `MascotPicture` in `core/designsystem/component/MascotWalk.kt` for the wave, `MoonwalkPicture` in `MascotMoonwalk.kt` for the moonwalk. `MascotPicturesTest` fails if the pictures' size, the sheet's rows, the animated picture's frames or how often it plays are not what stands there. Where he stands in a frame and how far he reaches are not in the files, and no test can check them.
- **The dials' black faces are drawn without their shine** in every picture (`dull_the_dials` in `render_greeting.py`; nothing is saved). They are glossy in the model, and as the mascot came round to the viewer the big dial mirrored the key light: pale grey for three frames, like a flicker.
- **`render_greeting.py` is where the camera, the stage and the halo are,** and every script that draws a picture reads them from it. Run by itself it draws the wave the app had from 2026-10-09 to 2026-10-11: one cycle of the clip `Walk` toward the left, and a turn to the viewer with the clip `Wave`. Nothing packs those frames for the app any more: `pack_greeting.py`, which did, is now only what the other packing scripts share.

To give the app another greeting, draw it as two more pictures the same way, an animated picture for the part in which the mascot stands where it is and a sheet of frames for the parts in which the app has to move it in step, and add it as the moonwalk was added: a value of `MascotGreetingKind`, its numbers beside `MoonwalkPicture`, and its files in `Mascot.kt` (`core/designsystem/component/`). A frame of the mascot at this size is 10 to 12 KB however it is packed, so each second of a clip is about a quarter of a megabyte; `MascotPicturesTest` holds the moonwalk under one megabyte and the wave under 1.2.

**Two runs of Blender do not give the same bytes.** Drawn twice from the same file on the same Mac, every frame differs from its twin in a few dozen pixels, by 3 of 255 at most, and the packed pictures differ by a few bytes in 800,000. So the app's pictures cannot be checked by drawing them again and comparing the files: compare the frames, and expect that much. (Packed twice from the same frames, a picture is the same bytes.) A change that is not meant to touch them leaves the committed pictures alone.

## Any other clip as a picture

`render_clips.py` draws any clip by its name, the standing mascot as many pixels high as it is told. It is the greeting's camera, lights, dulled dials and 24 frames a second, on a see-through background, so the mascot looks the same in every picture:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_clips.py -- 400 /tmp/milo-poses Flex Peace RockOn
python3 design/mascot/scripts/pack_clips.py poses /tmp/milo-poses
```

- **The first step draws the frames** (five minutes for these three), each clip into a folder of its own. All the clips named in one run get one size of picture, with the mascot standing on the same spot in each, so that one can be cut after another without a jump. The size is measured: as far as the mascot reaches in any frame, 12 pixels more on every side, and made up to a multiple of 8. For these three it is 592 by 448 pixels. It saves nothing in the Blender file.
- **The second step packs them** into `renders/`: an animated WebP of each that plays once and keeps its last frame, and a PNG still of the pose it holds. It needs what `pack_greeting.py` needs.

| Picture | Frames | Size | Its still |
|---|---|---|---|
| `renders/milo_flex.webp` | 109 | 1.48 MB | `milo_flex.png`: both fists up, the first squeeze |
| `renders/milo_peace.webp` | 67 | 0.96 MB | `milo_peace.png`: the peace sign, upright |
| `renders/milo_rock_on.webp` | 67 | 0.95 MB | `milo_rock_on.png`: the horns, between two nods |

For another size, give another number; for another clip, give its name and add a line to `POSES` in `pack_clips.py`.

## The website's pictures

The website's front page shows the mascot in its lime tile. His needles keep moving, and he waves for as long as a pointer is on him (APP_ENCYCLOPEDIA, Website). Three pictures in `website/mascot/`, drawn 560 pixels high so that the page can show him 280 high on a sharp screen:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_clips.py -- 560 /tmp/milo-web Wave WaveUp WaveLoop
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_dials.py -- 560 /tmp/milo-web Wave WaveUp WaveLoop
python3 design/mascot/scripts/pack_clips.py website /tmp/milo-web
```

The second step is given the same height and clips as the first, so that it measures the same picture. `Wave` is named although the page no longer shows it: see "where he stands in the picture" below.

| Picture | What it is | Size |
|---|---|---|
| `website/mascot/standing.webp` | The first frame of `WaveUp`: the mascot standing, on a see-through background. 720 by 608 pixels | 31 KB |
| `website/mascot/dials.webp` | Four squares of 128 pixels: the two dials' faces without a needle, cut round, and under them the two needles' blades, pointing straight up | 8 KB |
| `website/mascot/waving.webp` | 11 frames one under the other in one still picture of 720 by 6,688 pixels: five of `WaveUp`, then six of `WaveLoop`, every second frame of each. Not see-through where the standing mascot is (below) | 228 KB |

- **Nothing here is an animated picture.** The website runs no script (ADR-003, and its privacy page says so), and a page without one cannot start an animated picture again from its first frame. So the stylesheet does the moving (`website/styles.css`, "The mascot"), as the app steps through the sheet of its walk.
- **The needles are turned, not drawn frame by frame.** Only the needles move while he stands, and a needle only turns. So `dials.webp` holds each dial's face without a blade, which the page lays over the standing picture's dial, and the blade alone, which the page lays over that and turns about the middle of the dial. It weighs 8 KB; a strip of the two dials drawn frame by frame was reckoned at 150 KB or more and was not made. And the needles move as smoothly as the screen can show. How they move, an engine ticking over and revved now and then, is written in the stylesheet, not here.
- **How a blade is drawn alone** (`render_dials.py`): the needle is one object, a red blade and a black cap. In memory it is taken apart; the standing mascot is drawn with the caps only, and then the blades alone with everything else hidden and the caps as holes, because a cap lies over its blade. The blade is drawn without the glass over the dial, which would be drawn round it as a haze, and `pack_clips.py` darkens it by what the glass takes (a twentieth). The camera looks at the dials almost squarely, about 6 degrees off, so a blade turned flat on the page lies where a blade drawn at that angle would, to within half a pixel by the arithmetic. Turned to where the standing picture has its own needle, it covers it.
- **The wave is a raise and a loop in one strip.** The stylesheet steps through the first five frames once and then through the other six again and again, twelve a second. The raise's first frame is left out, because it is the standing pose; its last is the loop's first.
- **Each frame of the strip covers the standing mascot,** needles and all. A page without a script cannot tell whether the strip has arrived, so it cannot take the standing picture away for it: with the first version, on a slow line, the mascot was gone for two and a half seconds at the first rollover. So the standing picture stays, and each frame is filled with the lime of the tile wherever the standing mascot is and the waving one is not: his hanging right arm, most of all. `pack_clips.py` reads that lime from `--accent` in `website/styles.css`. **Change that colour, or stand him on another, and `waving.webp` has to be packed again,** or a shape in the old lime waves with him. WebP cannot store this lime exactly: the strip's is one step of 255 off in red and in blue, which cannot be seen.
- **The page is told the sizes and the places.** `pack_clips.py` prints them: the frame's size, how many frames the raise and the loop have, and for each dial where its square lies in the picture and where in the square the needle turns, in hundredths. `index.html` holds half the frame's size as the picture's width and height; `styles.css` holds the rest. Draw him another size, change a clip's length, or change the clips he is drawn from, and those change with it.
- **The sides are multiples of 8 pixels on purpose, and the dials' squares lie on multiples of 4,** so that half and a quarter of them are whole numbers of the screen's pixels, also on a screen scaled by a quarter or a half more, as Windows laptops are. Otherwise he would shiver by a part of a pixel from frame to frame, and a dial's face would lie a part of a pixel off the dial under it.
- **A picture that changes gets another name.** A visitor's browser keeps these pictures for a week and the stylesheet for an hour (`firebase.json`). When a picture changes in a way the stylesheet counts on, some visitors would for a week see the new rules step through the old picture. So the strip is `waving.webp` since it has 11 frames; it was `wave.webp` while it had 24.
- **Where he stands in the picture must not change either,** for the same reason: the dials' faces are laid over `standing.webp`, old or new. The picture's size and the mascot's place in it are measured from the clips named, as far as he reaches in any of them. `Wave`'s hand reaches 2 pixels further than `WaveLoop`'s, and the first `standing.webp` was drawn from `Wave`; so `Wave` is still named, its frames are drawn and not used, and he stands where he stood: his middle 434 pixels from the left of 720, his soles 11 above the lower edge.

## What to know before changing the skeleton

- The character faces Blender's −Y and stands on z = 0, about 2 units tall. `.L` is its own left.
- **The needles are driven by two sliders on the skeleton** (`speed_kmh`, `rpm`). A clip keys the sliders, never the needle bones.
- **The brake lights are driven by a third slider, `brake`,** from 0, unlit, to 1, full on. A clip keys the slider, never the lenses' material. Only the moonwalk's clips, the run's and the four of the walk in and off key it (see "The brake lights").
- **The hat is put on by a fourth slider, `hat`:** 0 is no hat, 1 is the hat. Only the moonwalk's clips key it (see "The hat and the moonwalk").
- **Three more sliders are for the party:** `party_hat` puts the party cap on, `popper` puts the party popper in his left fist, and `pull` puts the bead on the popper's string into his right hand's fingers. All three are 0 where the file is saved. Only `OneYear` keys them (see "The party").
- **The bone `root` turns the whole mascot, feet and all,** about the point he stands on. Only the moonwalk's clips key it, for the skid and the turn, the run's, for the slide, and the four of the walk in and off, in which it says which way he faces all the time. Every other clip leaves him facing the way he was built.
- **Arms and legs are hoses.** Their four bones on each side are bendy bones that carry no mesh; rows of short `hose.*` bones follow them and carry it. A `.glb` file cannot hold a bendy bone, and this is what keeps the hoses round in the export.
- **`Walk` walks on the spot,** with a short stride: each foot goes 0.30 of a unit back while it is planted, on a mascot 2 units high. The greeting moves the picture by that much, so a longer or shorter stride here changes how long the walk across the screen takes.
- **The feet lead the legs.** Move a foot and its leg follows; lower the body and the knees bend.
- The "M" is the app's own mark: its outline is read from `app/src/main/res/drawable/ic_launcher_foreground.xml`, and the numbers on the dials are set in the app's `sora.ttf`.

## The brake lights

Two round red domes low on his back, close together and proud of the body, where a truck has its tail lights and he would have his behind (Shawn, 2026-10-10: "add brake light to his back side like a butt. I would like these to be able to light up"). He chose them from three designs drawn for him ("use A for the brake lights"); the other two are not in the script (FINDINGS_LOG, 2026-10-10). Nothing shows from the front. The app's moonwalk is where they are seen, lit; the wave and the website show him from the front and from his left.

- **The lenses are one object, `Brake_Lights`, and their red is one material of its own, `MilO_Brake_Lens`:** a dark, glossy red while it is unlit, in thin rims of the black rubber.
- **They cast no shadow,** like the glass over the dials. The studio's rim light stands behind him; with a shadow, the lights darkened the top of a shoe by a few hundred pixels in every picture taken from the front.
- **None of the pictures there were changes.** Rays cast from the camera to every point of the lenses, in every frame of every clip as it is drawn, reach none of them; and the greeting's and the website's frames, drawn again, are the committed ones to within what two runs of the renderer differ by.
- **Lit, the lens glows:** the material's emission strength follows the slider `brake`, from 0 to 4 (`BRAKE_GLOW`), in a red kept so pure that a lit lens is drawn red and not pink (`lens_material`). The light also falls on the body round the lens.
- **A clip that lights them keys the slider itself.** In `animate_milo.py` it calls `set_brake(how far)` at every frame and `key_brake(frame)` after `key_pose(frame)`. The slider is not part of `key_pose`: that would add a curve to every clip there is. The moonwalk's clips do it (`key_clip` keys the pose and, for them, the lights, the hat and the turn), the run's and the four of the walk in and off do it (the lights and the turn, `key_run`), and `render_lights.py` does it in clips of its own, in memory. When a clip is drawn, the lights are put out and the hat is taken off first (`reset_pose`), so one that does not key them is not drawn with what the last one left behind.

**The halo.** Blender's renderer draws no glow round a light. `on_ground` in `render_greeting.py` adds one when a picture is drawn: from what the frame emits and from nothing else, so that no highlight on the chrome or the gloss gets one; and it is added to the frame and to a flat ground under it, as light is. It has to be asked for:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_clips.py -- 400 /tmp/milo-lit <clip> --ground 121316
```

- **`--ground` and a colour** draws the frames on that colour (`121316` is the app's dark page) instead of see-through, with the halo wherever a clip lights the lights, and with a fifth of his height more margin so that the halo has faded before the picture ends.
- **Without `--ground` nothing of it happens.** The greeting's and the website's pictures are drawn without it and are what they always were; that was checked by drawing them again (FINDINGS_LOG, 2026-10-10).
- **A see-through picture gets its halo another way** (`halo_see_through` in `render_greeting.py`, built for the moonwalk's preview). Light is added to whatever lies behind, and a see-through picture cannot store that: it stores a colour and how much of it covers what lies behind. So beside the mascot the halo is stored as pure red that covers a part, as much as the halo would be bright on black; where the mascot is, it is added to him as light. Over the app's dark page that is the halo all but exactly. **Over a light ground it is a red mist that darkens what it should light: it is for dark grounds only.** The halo's faint far part is left out (`HALO_ENDS`), so that it ends inside the picture and the picture's edge does not show; `render_moonwalk.py` stops if anything is drawn on a picture's border.
- **The halo shows on a dark ground.** On the lime and on white it all but disappears, and the lights then read as lit by their own brightness and by the warm light on the body.

**The pictures in `renders/`** are drawn and packed like this, 2.7 MB in all:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_lights.py -- /tmp/milo-lights
python3 design/mascot/scripts/pack_clips.py lights /tmp/milo-lights
```

| Picture | What it shows | Size |
|---|---|---|
| `milo_back.png`, `milo_back_lit.png`, `milo_back_three_quarter_lit.png` | His back with the lights off; lit; and from behind and to one side, lit. See-through, 552 by 584 pixels, no halo | 0.3 MB each |
| `milo_back_on_dark.png`, `milo_back_lit_on_dark.png`, `milo_back_three_quarter_lit_on_dark.png` | The same three on the dark page with the halo, 776 by 808 pixels | 0.4 to 0.5 MB each |
| `milo_turn_around_on_dark.webp` | Once round in 36 frames and 3 seconds, on the dark page, the lights coming on while his back is to the viewer. 464 by 488 pixels, starts again for ever | 242 KB |

The turn-around is a turntable, not an animation: the whole mascot is turned about the point he stands on, feet and all, as `render_greeting.py` turns him for the walk. (The moonwalk's clips turn him round themselves, with the bone `root`.)

**In the `.glb`.** A `.glb` holds meshes, materials, a skeleton and the skeleton's movement. It holds neither a slider nor the rule that ties a material to one, and Blender's exporter does not write a glow that follows a slider (tried with its "pointer animation" switched on: no difference). So a bone, `brake`, which carries no mesh, grows with the slider from size 1 to size 2, and that is in the file for every clip that keys the slider. The lens's lit colour is not in the file: unlit is how it is exported. Whoever plays the `.glb` reads the bone's size and lights the material `MilO_Brake_Lens` itself: emissive colour (1.0, 0.004, 0.002) times 4 times (size less 1).

## The hat and the moonwalk

**The app's second greeting, since 2026-10-10.** Shawn that day: "i want to have a animation with him moon walking on to the screen stops mid way. adoes some hip thrusts than spins and continues moon walking. [...] if we can add a hat for this animation it would be cool. i want it to be like Micheal Jackson video", to "take turns with the wave", and "show me a preview first". He was shown the preview below and said "it looks good"; the app plays what it showed. What is here is the hat, the four clips, the script that draws their frames, the script that packs them for the app, and the script that makes the preview.

**The hat** is a black fedora with a band in his own green (`build_hat_bm` in `build_milo.py`; the object `Hat`, the felt `MilO_Hat_Felt`).

- **It is as wide as he is,** because his head is two dials side by side: both go up into its crown. It rests on the small dial and is pulled down over the big one, so it sits at a slant, and it is tipped forward, low over both. The brim's front edge ends above the middle of each dial: the needles and the lower numbers stay in view.
- **The felt is nearly black, not black, with a soft sheen along its edges.** He is shown on the app's dark page, where a plain black hat is a hole in the picture.
- **A slider, `hat`, puts it on:** it is the size of the bone `hat`, which carries the hat. At 0 the whole hat is one point inside the dials and nothing of it is drawn. The Blender file is saved with it at 0, and `reset_pose` sets it to 0 before any clip is written or drawn, so every clip that does not ask for the hat is without it. The moonwalk's clips set it to 1 at every frame (`set_hat`) and key it.
- **By hand in Blender's window it lingers.** A clip chosen after one of the moonwalk's says nothing about the hat, the brake lights or the turn, so it is shown with what the moonwalk left: the hat on, the lights lit, he a little turned. `show("Idle")` in `animate_milo.py` puts that right, or setting the sliders `hat` and `brake` to 0 and the bone `root` back by hand. Every script that draws a picture does it before every clip, and `export_milo.py` does it before the file is saved (the exporter plays every clip, and had left the hat on in the saved file).
- **None of the pictures there were changes:** the twelve clips there were are the same in the Blender file and in the `.glb`, and the greeting's and the website's frames, drawn again, are the committed ones to within what two runs of the renderer differ by, and one step of 255 more in four single pixels (FINDINGS_LOG, 2026-10-10).
- **The hat is not in the `.glb`.** A `.glb` has no slider: a clip that says nothing about the hat shows it as it was built, on his head, and Blender's exporter writes nothing for a part that keeps still through a clip. He would wear it in every clip. The bone `hat` is in the file and the moonwalk's clips move it, so a hat can be hung on it.

**The clips** are in the table above. What they are built on:

- **The moonwalk is drawn on the spot, as `Walk` is,** and whoever shows it moves the picture: a 14th of the stride with every frame, to the left. The foot that is up on its toe then stays where it is on the screen. That foot's toe goes forward under him at an even pace, which is the floor going by; the flat foot slides back twice as far over the floor as he glides.
- **He does not glide straight back, but 35 degrees to his right of it** (`MOON_SLANT`). The pictures show him half turned to the viewer and move him level across the screen. Gliding straight back he would be going away from the viewer as well, and the foot that should stay put crept 7 dp up and down the screen. For the same reason the foot farther from the viewer goes a little further than the nearer one (`MOON_FAR`): it is drawn smaller, and its way with it. Both are right for the angle he is drawn at and for no other.
- **His hands are at his waist while he glides,** clear of his feet, which are what a moonwalk is watched for.
- **The brake lights cannot be seen from the front,** and he glides with his front to the viewer. So he stops with a skid: his back end swings round to the viewer, and that is where the lights are seen to come on. They are seen again as his back goes past in the turn.
- **The turn takes 13 frames,** a little over half a second, and goes at an even pace through its middle, 37 degrees a frame at most, so that every frame is a clear step on from the last. Faster, it is a smear.
- **The hip thrusts are seen from the side or not at all:** the foot of the body goes forward and its top goes back, 22 degrees, while he is turned three quarters to the viewer. Facing the viewer there would be nothing to see.
- **A hand that holds the hat or the belt is turned with the body** (`carried`). The arm poses are directions in the room, not from the body: without it the hand is left behind when the body leans.
- **The hand at the hat covers the upper corner of the big dial.** It is at the brim's corner on the side the viewer sees, where it hides least; the other side of the hat is behind the small dial from there.

**The preview** is drawn and laid over a picture of Home (`marketing/screenshots/home.png`) like this:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_moonwalk.py -- /tmp/milo-moonwalk 2.625
python3 design/mascot/scripts/preview_moonwalk.py /tmp/milo-moonwalk /tmp/milo-moonwalk-preview
```

- **The first step draws the frames** (two minutes): one cycle of the glide, and the three other clips one after the other, on a see-through background with the halo, he turned 55 degrees to the right. 2.625 is how many pixels the picture of Home has to the dp; without it the frames have 2.5, as the app's pictures do. It prints, and writes into `moonwalk.json`, what the second step needs: the picture's size, where he stands in it and how far a cycle carries him. It also prints how well the foot on its toe keeps its place.
- **The second step writes** `moonwalk.mp4` (the whole screen, 1080 pixels wide), `moonwalk.gif` (the foot of the screen, smaller), `moonwalk-sheet.png` (a frame from every quarter second) and four large stills. It plays the greeting as the app plays the one it has: he is 150 dp high, his soles are 4 dp above the bottom bar's tile, 24 frames a second, the app's soft shadow under his feet. He comes in from just beyond the right edge, does the middle part in the middle of the screen and glides out at the left: 7.3 seconds on a phone 411 dp wide.
- **Nothing of the preview is committed.** The frames and the film are written wherever the two commands are told.

**The app's two pictures** are made from the same frames, drawn with 2.5 pixels to the dp, which is what `render_moonwalk.py` draws when it is told nothing else:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_moonwalk.py -- /tmp/milo-moonwalk-app
python3 design/mascot/scripts/pack_moonwalk.py /tmp/milo-moonwalk-app
```

| Picture | What it shows | Size |
|---|---|---|
| `app/src/main/res/drawable-nodpi/mascot_moonwalk.webp` | One cycle of the glide toward the left, 14 frames. A still picture of 3,360 by 960 pixels: the frames lie side by side, seven across and two down. The app draws the frame it wants, because it moves the mascot with every frame, in and out | 130 KB |
| `app/src/main/res/drawable-nodpi/mascot_stop_and_spin.webp` | The middle part, from the glide's first frame: `MoonwalkStop`, `KickSpin` and `MoonwalkOn`, 80 frames. An animated picture, which Android plays once | 768 KB |

- **The first step draws the frames** (two minutes), 560 by 592 pixels each. The second packs them, in seven seconds, and needs Pillow (`pip3 install pillow`), `cwebp` and `webpmux` (`brew install webp`).
- **The app's code has to be told what the second step prints:** `MoonwalkPicture` in `core/designsystem/component/MascotMoonwalk.kt` holds those numbers: a frame's size, where his axis is in it, where his soles are, how far he reaches to each side while he glides, and his stride. `MascotPicturesTest` fails if the pictures' sizes, the sheet's rows, the animated picture's frames and time or how often it plays are not what stands there. The stride, the reaches, the axis and the soles are not in the files, and no test can check them: a wrong stride shows as a toe that slides.
- **`pack_moonwalk.py` does four things the old wave's packing (`pack_greeting.py`) did not,** and says why at its top; `pack_wave2.py` packs the wave of 2026-10-11 with these same functions:
  - **It cuts the frames down** to what is drawn in any of them and 8 pixels more, made up to 480 by 480 pixels (192 dp). The room the first step leaves for the halo is mostly empty. It cost no bytes, but it cost the phone memory: the sheet is 12.9 MB there instead of 18.6.
  - **A frame with a glow in it keeps its see-through channel almost whole** (20 of the 80: the skid and the turn). Stored as the wave's pictures store it, in twelve steps, an edge is fine and a soft halo is drawn as rings.
  - **A frame that is the frame before is not stored** (2 of the 80): the frame before is shown twice as long.
  - **A frame in which only the needles move is stored as the small box that changed** (5 of the 80), laid over the frame before. So the picture holds 78 stored frames, and a frame may depend on the one before it.
- **Colour is packed at 50 of 100,** where the old wave's was at 60: he is never still in these pictures, and at a phone's size a frame at 50 could not be told from one at 60. At 40 the numbers on the dials and the lines between his fingers smear. (The wave of 2026-10-11 is packed at 50 too.)
- **The script checks itself:** it plays the packed picture back and holds every frame against the one that was drawn, over the app's dark page. A patch in the wrong place stops it.
- **The halo makes these pictures right for a dark ground only.** Over the app's lime tile the glow is an orange mist that darkens the lime (FINDINGS_LOG, 2026-10-10).
- **A change to a clip, to the hat or to the lights** means both steps again, the numbers into `MoonwalkPicture` if any changed, and a look at the greeting on a phone or an emulator.

## The party: a year of use

**The app plays it since 2026-10-10:** on the day of the first recorded trip, a year on and every year after, and on demand (APP_ENCYCLOPEDIA, "The one-year party"). Shawn, 2026-10-10: "a one year use animation. After the person has used the app for a year this one will come up that day every time he starts a trip. i want it to fade in with the mascot in the center with a birthday cap. i want him to put his hand behind his back and pull out a party popper and pull it and shoot confetti above his head. i want sound effects for this". He was shown the preview below, with sound, and approved it; the app plays what it showed. What is here is the cap, the popper, the confetti, the clip, the two scripts that make the preview and the script that packs the app's pictures. The pop itself is not made here: `tools/make_party_pop.py` writes it.

**The cap** is a paper cone in the app's lime with stripes of his own green that wind round it, a white rolled rim and a red pom-pom (`build_cap_bm` in `build_milo.py`; the object `Party_Cap`).

- **It sits on the big dial,** the highest part of his head, a little towards his right, and leans a little further that way and back. A cone cannot be as wide as he is, as the fedora is. Its rim is not flat: it follows the dial's round housing, so the cap sits on it from every side.
- **Lime and white stripes were tried and dropped:** on a white ground the cap was all but gone. With the green stripes and the red pom-pom it holds on the dark page and on white.
- **A slider, `party_hat`, puts it on,** as `hat` puts the fedora on: it is the size of the bone `party_hat`, which carries the cap, and at 0 nothing of the cap is drawn. The two hats do not know of each other: with both sliders at 1 he wears both, one through the other.
- **The cap's bone hangs on the big dial's bone,** not on the body: a clip that turns or swells that dial takes the cap with it. `OneYear` also tips and tilts the cap a moment behind whatever his head does (`set_party`), by a few degrees; more, and its rim leaves the dial.

**The popper** is a red cone with a white band and a lime lip, on a handle, with a knob on its tail and a string with a bead out of that (`build_popper_bm`; the object `Popper`).

- **It lies in his left fist like an ice-cream cone:** its mouth comes out on the thumb's side, its tail and the string on the other. The handle is inside the fist, where the fingers are; his hands have no room in them for a handle, and none is seen.
- **A slider, `popper`, puts it there,** the same way: the size of the bone `popper`. The bone reaches from the middle of the fist to the popper's mouth, so its far end is where the confetti comes out.
- **A third slider, `pull`, is the string.** At 0 the bead hangs straight out of the tail. At 1 it is where the right hand pinches (the bone `grip.R`), wherever that hand is, and the string reaches after it, as long as it has to be. In between it is on its way. A real popper's string does not stretch; this one does for two frames, as he pulls.
- **Which hand holds and which pulls** is said once, at the top of `build_milo.py` (`POPPER_SIDE`, `PULL_SIDE`). The clip is written for the left hand holding; the other way round, the fist would be in front of the big dial.

**The confetti** is a collection of its own, `MilO_Confetti`: 130 pieces of paper, 6 curled streamers and the flash at the popper's mouth, in six colours, the app's lime twice as often as the others (`build_confetti`).

- **It is one mesh on a skeleton of its own,** `MilO_Confetti_Rig`, a bone to a piece. The pieces are cut and coloured from a seed, so every build makes the same ones.
- **The collection is hidden,** in Blender's window and in every picture, unless a script asks for it: no picture there was has confetti in it. `render_party.py` asks. To see it in Blender's window, switch the collection on in the outliner and play `OneYear`.
- **Its flight is worked out, not simulated** (`throw_confetti` in `animate_milo.py`): from one seed, the same every run, with no cache. The paper is blown out of the popper's mouth in a cone, is slowed by the air within half a second, and flutters down at the pace of falling paper; the streamers go further, unroll and hang as they fall. It is keyed on every frame for the first third of a second and on every second frame after.
- **It starts where the clip has the popper's mouth** in the frame of the pop, and points where the popper points. Move the hand in the clip and the confetti moves with it at the next run.
- **None of it goes through him.** He stands in a slab of room that the pieces are pushed out of, to the front or to the back (`SLAB_...`): they fall in front of him and behind him. From the front that cannot be told from falling round him. From the side it can.
- **It casts no shadow,** so that a picture of him alone with a picture of the confetti alone laid over it is nearly the picture of both. Not quite: a piece that is behind him is in the picture of the confetti alone.
- **It is not in the `.glb`,** which holds the `MilO_Mascot` collection only; nor are the cap and the popper, for the hat's reason. The bones `party_hat`, `popper`, `popper_string` and `popper_pull` are in the file, and `OneYear` moves them.

**The clip** is in the table above. What it is built on:

- **He fires the popper in front of his chest, not above his head.** His arms are short: his two hands only meet in front of his middle, and a hand held up beside his head is out of the other's reach. So he holds the popper up to show it, then brings it down in front of him, where the other hand can take the string. It is aimed up between the dials, a little forward and a little to his left, away from the cap; the confetti goes up past his face and opens over his head.
- **The hand really goes behind him.** From the front nothing of the glove is seen for fourteen frames, 30 to 43, and the popper appears in the fist there, upright, where his body hides it. Of the arm only the elbow shows, at his side: his arms are short.
- **It begins with half a second of standing still** (`PARTY_HOLD`), for whoever shows it to fade it in on, and comes to rest over its last second with both arms up, for a fade out. The confetti is still coming down when it ends.
- **The frame of the pop is `PARTY_POP`,** 73. The right hand starts its pull a frame and a half before; on that frame and the next the string is stretched after it, and by the one after it has sprung back; the flash and the first paper are at the mouth. A sound's bang belongs on that frame.
- **The needles** flick up when he bobs, tremble while he feels about behind his back, rise for the popper, fall to nothing while he aims, climb to the red line as the string comes tight, and go right round when it goes off.
- **An arm can be said by where its wrist is** (`reaching`, and `holding` and `pinching`, which say where the popper's fist or the pinch is): the popper has to be in a place, and the two hands have to meet. They give an ordinary arm pose, which is blended like any other.

**The preview** is drawn and made into a film like this:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_party.py -- /tmp/milo-party
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_party.py -- /tmp/milo-party --cap
python3 design/mascot/scripts/preview_party.py /tmp/milo-party /tmp/milo-party-preview --name A --pop pop.mp3 --before rustle.mp3:-6 --after horn.mp3:-8
```

- **The first step draws the frames** (three and a half minutes): he and the confetti, 600 pixels high in a picture of 1080 by 1380, on a see-through background. The picture is a fixed room round him, not measured from what is in it: confetti that drifts out of it is to drift out of it. `--confetti without` draws him alone and `--confetti alone` the confetti alone, into folders of their own beside the first, in the same picture; `--every 3` and `--frames 60-84` draw a part, for a quick look, and `--turn 40` turns him and the confetti to look from the side. It writes `party.json`, which says what the third step needs: the picture's size, where he stands in it, and the frame of the pop; and it prints, and writes there too, where the popper's mouth is in the picture in that frame.
- **The second draws two stills of the cap,** from the front and three quarters on, into the same folder.
- **The third writes** `party-preview-A.mp4` (a phone's screen of 1080 by 2400, the app's dark page, he in the middle of it, the app's soft shadow under his feet), `party-preview.gif` (the middle of the screen, smaller, no sound), three sheets (a frame from every quarter second; every frame of the reach behind his back; every frame round the pop) and the cap's stills on the dark page and on white. He fades in over the half second he stands still and out over the clip's last half second. 5.71 seconds in all.
- **The sounds are given to it, and it lays them:** `--pop` is the bang, and the script finds where the file is loudest and lays that on the frame of the pop, to the sample; `--before` is a rustle that begins as the hand comes out from behind his back, `--after` a horn or a cheer that begins seven frames after the bang. A colon and a number after a file makes it that many decibels louder. Without any, the film is silent. The first previews were made with generated sounds (ElevenLabs), whose free plan does not allow them to be shipped; none of them is in this repository. **The film Shawn approved has one sound, the pop, made by arithmetic alone,** and that is the sound the app carries (`tools/make_party_pop.py`, `app/src/main/res/raw/party_pop.wav`).
- **Nothing of the preview is committed** but the four pictures in `renders/`, which are drawn small and packed like this (2.9 MB together):

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_party.py -- /tmp/milo-party-small 280
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_party.py -- /tmp/milo-party-small --cap
python3 design/mascot/scripts/pack_clips.py party /tmp/milo-party-small
```

| Picture | What it shows | Size |
|---|---|---|
| `milo_party_cap.png`, `milo_party_cap_three_quarter.png` | Him standing in his cap, from the front and turned 40 degrees to his left. See-through, 560 by 768 pixels | 0.4 MB each |
| `milo_one_year.webp` | The whole clip with its confetti, 131 frames of 504 by 644 pixels, he 280 high. See-through; plays once and keeps its last frame | 1.9 MB |
| `milo_one_year.png` | Frame 84 of it: both arms up, the confetti over his head | 0.2 MB |

`render_clips.py` draws `OneYear` too, like any clip: him alone, in a picture measured from how far he reaches.

**The app's two pictures** are made from the film's own frames, he 600 pixels high, which is what `render_party.py` draws when it is told nothing else:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_party.py -- /tmp/milo-party
python3 design/mascot/scripts/pack_party.py /tmp/milo-party
```

| Picture | What it shows | Size |
|---|---|---|
| `app/src/main/res/drawable-nodpi/mascot_party_popper.webp` | From the frame he stands still in to the last one before the popper goes off: the clip's frames 12 to 72, 61 of them. An animated picture, which Android plays once | 885 KB |
| `app/src/main/res/drawable-nodpi/mascot_party_pop.webp` | From the frame the popper goes off on: frames 73 to 129, 57 of them. An animated picture, which Android plays once | 1,426 KB |

Together 2.31 MB. A frame is 675 by 800 pixels.

- **The first step draws the frames** (three and a half minutes), the second packs them, in twenty seconds, and needs Pillow (`pip3 install pillow`), `cwebp` and `webpmux` (`brew install webp`).
- **The app's code has to be told what the second step prints:** `MascotPartyPicture` in `core/designsystem/component/MascotPartyPicture.kt` holds those numbers: a frame's pixels, how many pixels it has to the dp, where he stands in it, how high he is, and how many frames each picture is. `MascotPicturesTest` fails if the pictures' sizes, frames, times or how often they play are not what stands there. Where he stands is not in the files, and no test can check it.
- **The clip is cut in two at the frame of the pop.** Android plays an animated picture by its own clock and does not say which frame it shows. The app starts the second picture itself, and that moment is the pop: the sound is asked for in the same drawing of the screen.
- **What `pack_party.py` does to the frames,** and says why at its top:
  - **It makes them smaller.** The film's frames have him 600 pixels high, a phone's own pixels. In the app's pictures he is 375 pixels high, as many as the greetings' mascot, and the app shows them larger: as large as the film showed him. That is 1.64 pixels to the dp where the greetings have 2.5, and he is a little softer for it. `APP_PIXELS` at the script's top is that number: at 450 the two pictures weigh 2.87 MB, and at the film's own 600, 3.29 MB (`MascotPartyPicture` then has to be told the new pixels).
  - **It cuts the room's top off,** the 100 pixels of the film's frame in which nothing is ever drawn. That cost no bytes, but it cost the phone memory.
  - **It leaves the clip's last frame out:** the app has faded him out by then.
  - **It leaves out the frames he stands still in, but the last.** The clip's first thirteen frames are one picture. Stored once and to be shown for thirteen frames' time, Android let it go after about a tenth of a second (seen on an emulator), and the popper went off half a second early. So the app shows the first picture's first frame itself for that long, and then lets Android play the picture.
  - **A frame that is the frame before is not stored, and a frame that barely changes is stored as a patch,** as `pack_moonwalk.py` does: 1 and 15 of the first picture's 61, 1 of the second's 57.
  - **The confetti's frames are packed harder than his:** colour at 28 of 100 and the see-through channel at 30, where the frames before the pop have 40 and 50. Paper in the air is small, bright and gone in a twenty-fourth of a second.
  - **A frame that is fading is packed harder still,** in proportion to how strongly the app shows it.
- **The script checks itself,** as `pack_moonwalk.py` does: it plays each packed picture back and holds every frame against the one that was drawn, over the app's dark page.
- **What was tried for the weight** is in FINDINGS_LOG, 2026-10-10: other sizes, other qualities, and him and the confetti as two pictures laid over each other, which weighed the same and was not his film.
- **He faces the viewer and no brake light is lit,** so there is no halo, and these pictures are right on any ground: over Home's lime tile as over its dark ones.
- **A change to the clip, the cap, the popper or the confetti** means both steps again, the numbers into `MascotPartyPicture` if any changed, a look at the party on a phone or an emulator, and a new film for Shawn if what he approved has changed.

## The run and the slide

**In the app since 2026-10-11: he runs across the foot of the Report screen when a report has been sent** (APP_ENCYCLOPEDIA, "The run after a report is sent"). Shawn, 2026-10-10: "i was thinking one where the mascot runs on to the screen. than he runs in one spot in the center for 2 seconds. than sort of turns and slides out. if we can make smoke from his shoes or something visual that would be cool. the lights on his butt should be off tell he stops and slides and than turn on like a car stopping. im not sure where to use this animation yet"; about the turn, "he doesnt full turn his back towrds us fully"; and "sure a sound effect squeal would be cool". He approved the film of the preview as it is, and on 2026-10-11 said where it plays: "i want this animation included. and used when the user completes sending a report by email". What is here is five clips, the smoke, the two scripts that make the preview, and the script that packs the app's three pictures ("The app's three pictures", below).

**The clips** are in the table above: `Run`, `SpinUp`, `Burnout`, `BrakeSlide`, `Slide`. What they are built on:

- **All five are drawn on the spot,** as `Walk` and `Moonwalk` are, and whoever shows them moves the picture. How far is said in `animate_milo.py`, because the smoke needs it too (`run_travel`): a cycle of the run carries him 1.8 units, a frame 0.225; in `SpinUp` he arrives at that pace and has stopped going by its fourth frame, 0.3 of a unit on; `Burnout` goes nowhere; in `BrakeSlide` he pulls 0.10 back as he crouches, is 0.62 on from the spot by the frame he brakes on, going 0.22 a frame, and from then on goes slower and slower, half the difference lost in three and a half frames, down to 0.10 a frame (`BRAKE_SPEED`, `SLIDE_SPEED`, `SLIDE_EASES`). `Slide` carries that on for as long as it is held.
- **He does not stop.** He was asked to slide out, so he brakes hard and then slides on at an even pace until he is gone: slow enough that the lit lights are on the screen for most of a second, fast enough to be out in a second and a half.
- **He goes 38.5 degrees to his left of straight ahead** (`RUN_SLANT`), and his feet go that way under him, as he glides 35 degrees to his right of straight back in the moonwalk and for the same reason: he is drawn turned 58 degrees to the right (`RUN_ANGLE` in `render_run_slide.py`), and the picture is moved level across the screen. The two angles together make his way level in the picture as the studio's camera sees it: the camera stands a little to one side, and level for it is 6.5 degrees off the room's own left and right. Moved a frame's part of the stride with every frame, a planted foot keeps its place to within half a pixel. Both angles are right for each other and for this camera, and for no other.
- **His hips are half as if square to the way he goes** (`RUN_SQUARE`, in `placed`). Half turned to the viewer, one hip is further along his way than the other, and its foot with it: every other step his shoes were drawn far apart, and the steps between close together, like a limp. All the way square, the two shoes were drawn on top of each other. Half is between the two.
- **His shoes are large and his legs short.** A shoe is two thirds of a unit long and higher than his legs are long; kicked up behind him as a runner's is, it is inside his body. So the run is a scurry: the feet stay low, and what shows the speed is how he leans, his fists, how often his feet come, and the bounce of a step with both feet off the floor. On the spot the shoes go round like wheels instead, six frames to a turn, and scuff back along the floor on their toes.
- **The fist nearer the viewer stays low and beside him** (`RUN_ARMS`). Swung as far forward as the other one, it went across the M and the dials with every step. It still covers the M's near edge for half of each step.
- **Nothing of him goes into anything else of him.** `check_run_slide.py` asks every frame of the five clips, with the meshes as they are posed (`blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/check_run_slide.py`): neither shoe and neither hand is in his body, in a dial's housing, in a shoe or in the other hand. (His arms and legs come out of his body, as they always do.) It took three things. He is carried a tenth of a unit high while he runs, his legs stretched: leaning the way he goes, the low corner of his body comes down a fifth of a unit over the shoe in front, and lower he stood in it. His elbows are well out, or a fist's fingers are in his side as it passes. And an arm that is back is bent at a right angle, which keeps its fist above the shoe under it, while one that is forward is bent less, which keeps its fist under the small dial.
- **Anticipation, then the bolt.** Before he goes he crouches back for four frames, his weight over the foot behind, the revs dipping; then he leaps off that foot, stretched, both arms thrown back. Five frames after the crouch he brakes.
- **The brake lights come on at once,** as a car's do, not over a few frames: in frame 9 of `BrakeSlide` (`BRAKE_ON`), and they stay on. A sound's squeal belongs on that frame. His back is then still edge-on to the viewer: on the dark page the halo is seen first, behind him; a frame later the lenses, and three frames after the brake they are fully in view.
- **He turns 80 degrees and no further** (`SLIDE_TURN`). With the 58 he is drawn at, that is 138 degrees from the viewer, three quarters of the way to a back view: the view of `milo_back_three_quarter_lit.png`, the other way round. The swing goes four degrees too far before it settles, to 142; a full back view would be 180.
- **He leans back against the slide,** 25 degrees and squashed as he brakes, 15 once he slides, both feet ahead of him, each under its own hip, the right one leading; he rocks once as he brakes and shudders every four frames for as long as he slides. His hands are flung out and open, their backs to the viewer, so that five fingers are seen and not the edge of a hand.
- **By hand in Blender's window it lingers,** as after the moonwalk: a clip chosen after `BrakeSlide` or `Slide` says nothing about the brake lights or the turn, and is shown with the lights lit and him turned. `show("Idle")` in `animate_milo.py` puts that right.
- **The needles** show a car flat out as he comes; go to the top as his wheels spin free on the spot, where they tremble; dip as his feet bite and jump as he bolts; and fall as he brakes, the speed to 26 and the revs to just above idle.

**The smoke** is a collection of its own, `MilO_Smoke`: 112 puffs, of which the clip uses 110 (`build_smoke` in `build_milo.py`).

- **A puff is three to five round lumps grown into one another,** like a small cloud; no fog, nothing see-through, nothing simulated. The file keeps twelve points to a lump, and the object rounds them when a picture is drawn (a subdivision modifier after the skeleton's).
- **It is one mesh on a skeleton of its own,** `MilO_Smoke_Rig`, a bone to a puff, as the confetti is. The puffs are shaped from a seed, so every build makes the same ones. A puff is built about one unit in radius: the size of its bone is how large it is drawn.
- **Pale grey, and greyer at its edge** (`SMOKE_COLOURS`, `smoke_material`): pale for the app's dark page, but not white, and each lump darker where its surface turns away from the viewer. On a white page a puff is then a grey shape with an outline. Most of its colour it glows by itself, so it is the same on any ground; the lights add a paler top. It glows too faintly to get a halo of its own, and the red of lit brake lights falls on it as on him.
- **The collection is hidden,** in Blender's window and in every picture, unless a script asks for it: no picture there was has smoke in it. `render_run_slide.py` asks. To see it in Blender's window, switch the collection on in the outliner and play: the smoke's clip runs by itself, whichever clip he is shown in.
- **What it does is worked out, not simulated** (`make_smoke`, `smoke_at` and `write_smoke` in `animate_milo.py`): from one seed, the same every run, with no cache. A puff comes from a shoe, is kicked away, slows, rises a little, swells, and shrinks to nothing: it is never faded. There are 12 of dust at the footfalls of the run in; 48 while he runs on the spot, one or two a frame from the shoe that scuffs, kicked back like the smoke of a spinning tyre, larger and longer-lived as the two seconds go by; 8 in a burst where he pushes off; 6 thrown up ahead of the shoe that leads as it bites; and 36 in the trail, from both shoes while he is fast and from each in turn after.
- **It stays where it was made.** The clips are drawn on the spot, so in the smoke's clip it goes back past him as fast as he goes forward. The burst he bolts out of is left behind in the middle of the screen, and the trail lies along the floor behind him.
- **Its clip, `RunSlide_Smoke`, is for the five clips shown one after the other in one order** (`act_pose`): `Run` for 24 frames, `SpinUp`, `Burnout` seven times, which with `SpinUp` is two seconds on the spot, `BrakeSlide`, and `Slide` for 32 frames. 126 frames; the brake lights come on in frame 81. Show them otherwise, longer on the spot or a longer way in, and the smoke has to be written again for that: `RUN_IN`, `BURNOUTS` and `SLIDE_OUT` say how long each part is.
- **It casts no shadow,** so that a picture of him alone with a picture of the smoke alone laid over it is nearly the picture of both. Not quite: a puff that is behind him is in the picture of the smoke alone.
- **It is not in the `.glb`,** which holds the `MilO_Mascot` collection only. The five clips are: the bone `brake` grows in `BrakeSlide` and `Slide`, and the bone `root` turns.
- **No skid marks.** Black marks under his shoes would not show on the dark page, and none were made.

**The preview** is drawn and made into a film like this:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide --smoke without
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide --smoke alone
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide --plain --frames 65-90 --every 25
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide --plain --frames 65-90 --every 25 --smoke without
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide --plain --frames 65-90 --every 25 --smoke alone
python3 design/mascot/scripts/preview_run_slide.py /tmp/milo-run-slide /tmp/milo-run-slide-preview
python3 design/mascot/scripts/preview_run_slide.py /tmp/milo-run-slide /tmp/milo-run-slide-preview --reuse --name A --squeal squeal.mp3 --spin burnout.mp3:-16
```

- **The first step draws the frames** (four minutes): all 126 of the five clips one after the other, he and the smoke, 394 pixels high, which is the 150 dp of the app's greeting on a screen with 2.625 pixels to the dp, on a see-through background with the halo, he turned 58 degrees to the right. Every frame is drawn on the spot. The picture is a fixed room round him of 1300 by 634 pixels, and much wider to his left than to his right: the smoke he leaves behind has to be in it for as long as it is on a screen. It writes `run-slide.json`, which says what the film needs: the picture's size, where he stands in it, which clip each frame is, the frame the brake lights come on in, and for every frame how far he is from the spot he runs on, in pixels. It also prints how level his way is in the picture and how well a planted foot keeps its place. `--every 3` and `--frames 70-100` draw a part, for a quick look; `--turn 40` turns him and the smoke further round, to look from another side.
- **The second and third draw him alone and the smoke alone,** into folders of their own beside the first, in the same picture.
- **The next three draw two frames of each again without the halo** (`--plain`), into folders of their own: frame 65, while he runs on the spot, and frame 90, in the slide. The halo is for a dark ground only, and these are for a white one. `milo_slide.png` is cut from one of them.
- **`preview_run_slide.py` then writes** `run-slide-preview-silent.mp4` (a phone's screen of 1080 by 2400, the app's dark page and nothing else on it, the app's soft shadow under his feet), `run-slide-preview.gif` (the part of the screen he is in, smaller), two sheets (a frame from every quarter second; every frame round the brake and the swing), a still of the slide on the dark page and on white, and two frames with the smoke, without it and the smoke alone. It moves the picture as `run-slide.json` says: he comes in from beyond the left edge, the spot he runs on is the middle of the screen, and he slides out beyond the right edge. 121 frames, 5.04 seconds: he gets to the spot in frame 19, 0.79 seconds in, the brake lights come on in frame 76, 3.167 seconds in, and the last of him has left the screen in frame 106, 4.417 seconds in; the last smoke goes a third of a second after.
- **The sounds the app has** are the two of the film he chose on 2026-10-11, the third of three films whose sounds were made in code: `tools/make_run_sounds.py` makes them and cuts them as this script cut them for that film (the whirr from where it begins, the fades, the whirr ten decibels under the squeal). They are in `app/src/main/res/raw/`, not here.
- **Run again with `--reuse` and a name, it lays sound on that film** and writes it beside it under that name. `--squeal` is the tyres' squeal: where it begins in its file is found, not assumed (the first 2 thousandths of a second that are within 20 decibels of its loudest and go on being), and the file is laid so that this lies on the frame the brake lights come on in; it is faded out over its last 0.3 seconds so that it has ended when the last of him has left the screen. `--spin` is a sound for the run on the spot, laid from the frame he gets there and faded out as he brakes. A colon and a number after a file makes it that many decibels louder. Every edge a sound is cut at is faded over a few thousandths of a second. **No sound a film was made with is kept in this folder.** The first films' sounds were generated (ElevenLabs), and their licence is not confirmed: they are in no part of the repository and cannot ship.
- **Nothing of the preview is committed** but the four pictures in `renders/`, which are packed from the frames and the film like this (0.9 MB together):

```
python3 design/mascot/scripts/pack_clips.py run-slide /tmp/milo-run-slide /tmp/milo-run-slide-preview
```

| Picture | What it shows | Size |
|---|---|---|
| `milo_run.webp` | One cycle of `Run`, he alone, drawn on the spot: 8 frames of 600 by 512 pixels, he 394 high. See-through; starts again for ever | 83 KB |
| `milo_slide.png` | Frame 90 of the whole, nine frames after the brake: the slide, the brake lights lit, he alone. See-through, 600 by 512 pixels, and no halo: right on any ground | 168 KB |
| `milo_slide_on_dark.png` | The same frame as the film shows it: the part of the screen he is in, 1080 by 634 pixels, on the dark page, with the smoke and the halo | 187 KB |
| `milo_run_slide_on_dark.webp` | The whole film at half its size, the part of the screen he is in: 121 frames of 540 by 318 pixels, 5 seconds, on the dark page. Starts again for ever; no sound | 510 KB |

`render_clips.py` draws the five clips too, like any clip: him alone, from the front, where he is seen to run towards his own left of straight ahead and to slide side-on.

**The app's three pictures** are made from the same frames, drawn with 2.5 pixels to the dp, he 375 pixels high:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide-app 375
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_run_slide.py -- /tmp/milo-run-slide-app 375 --smoke without
python3 design/mascot/scripts/pack_run_slide.py /tmp/milo-run-slide-app
```

| Picture | What it shows | Size |
|---|---|---|
| `app/src/main/res/drawable-nodpi/mascot_run_in.webp` | The run in, on the spot, with the dust behind his feet: frames 8 to 23 of the whole, its last two cycles. A still picture of 2,600 by 1,720 pixels: the frames lie side by side, four across and four down. The app draws the frame it wants and moves him with every frame | 152 KB |
| `app/src/main/res/drawable-nodpi/mascot_run_on_the_spot.webp` | Frames 24 to 80: from the frame he gets to the spot to the last one before the brake. An animated picture of 1,170 by 450 pixels, which Android plays once | 641 KB |
| `app/src/main/res/drawable-nodpi/mascot_brake_and_slide.webp` | Frames 81 to 125: from the frame the brake lights come on in. An animated picture of the same size, played once | 732 KB |

- **The first two steps draw the frames** (four minutes each): all 126 with the smoke, and all 126 of him alone, which the third step reads only for what it prints about a screen. Drawn again at the film's own size, three frames differed from the film's by 2 of 255 at most: the renderer's own noise.
- **The two animated pictures stand still on the screen, and he moves in them.** Android does not say which frame of an animated picture it shows, so the app cannot move one in step with its frames. `pack_run_slide.py` lays every frame where the film laid it, by whole pixels, on a picture that reaches from the leftmost smoke to 312 dp right of the spot he runs on: the smoke stands still in it, and the film's shadow is laid in under his feet. A frame is stored as the box that changed, so the picture's width costs nothing where nothing is drawn.
- **The second begins with the brake,** so that the app, which starts it, knows the frame the squeal belongs to. The first is started by the app too, on the frame he gets to the spot, for the whirr.
- **Both are cut to one box,** so the second takes the first's place without anything moving.
- **The sheet holds two cycles of the run,** where one would do for him: the dust behind his feet is not the same in both, and the film showed these sixteen frames on a screen 411 dp wide. On a wider screen the app begins earlier in the run and shows them round again.
- **The slide's picture ends 312 dp to the right of the spot.** By the frame after the last, one more step of the slide's even pace, the last of him has gone 310.7 dp past it. So the spot is the middle of a screen up to 620 dp wide (`RunPicture.SLIDE_REACH`), and no frame had to be drawn that the film did not have.
- **The app's code has to be told what the third step prints:** `RunPicture` in `core/designsystem/component/MascotRunPicture.kt` holds those numbers. The sheet's frame is 650 by 430 pixels, 260 by 172 dp; his axis is 167.6 dp from its left edge and his soles 4.8 dp above its lower edge; in the eight frames of a cycle he reaches 66.4, 68.0, 74.4, 83.6, 88.0, 86.4, 79.6 and 71.2 dp ahead of his axis; a frame of the run carries him 15.4525 dp, and in the frame he gets to the spot he is 20.604 dp short of it. The animated pictures are 468 by 180 dp; the spot is 156.0 dp from their left edge and his soles 8.4 dp above their lower edge; and for each of the slide's 45 frames, how far to the right of the spot the leftmost thing drawn is. `MascotPicturesTest` holds the three files to the sizes, the frames and the times; `MascotRunTest` holds the step and the first sixteen places against the film's own (`run-slide.json`, "travel"). The axis, the soles, the reaches and the smoke's left edge are in no test.
- **It is packed by `pack_moonwalk.py`'s functions, at its settings:** colour at 50 of 100; the see-through channel at 50, and at 80 for every frame from the brake on, which has the lights' glow.
- **The script checks itself:** it reads the three packed pictures back and holds every frame against the one that was laid, over the app's dark page: 0.94 of 255 a pixel on average at worst on the sheet, 0.67 and 0.71 in the two animated pictures.
- **The halo makes the slide right for a dark ground only,** as the greetings' is, and on the Report screen he slides past a lime button (FINDINGS_LOG, 2026-10-11). `render_run_slide.py --plain` draws the frames without the halo.
- **A change to a clip, to the smoke or to the lights** means the three steps again, the numbers into `RunPicture` if any changed, a look at the run on a phone or an emulator, and a new film for Shawn if what he approved has changed.

## The wave, walked through

**The app's first greeting, since 2026-10-11.** Shawn that day: "can we redo the first animation of the one where he walks onto the screen and waves. i want to use the model with the lights on his butt. and also after he turns and waves for him to continue walking off the screen. can we have hime start from the left side of the screeen walking off the right side". He was shown the two previews below, chose A and asked for no change; the app plays what film A showed, in place of the wave it had, and in turn with the moonwalk. What is here is four clips, the script that draws their frames, the script that packs them for the app, the script that makes the previews and the script that checks them.

**What he does.** He walks in from the left edge along the top of the bottom bar, seen three quarters from the front, so that his dials and the M show as he comes. In the middle of the screen he stops, and his brake lights come on, behind him. He hops round to face the viewer and waves: that is the clip `Wave`, as it is. He hops round to the right until his back is to the viewer, three quarters on, the brake lights lit; and he walks off at the right edge. He does not fade.

**Two ways with the lights were drawn for Shawn to choose between by eye, and he chose A.** In both they come on at once as he stops, as a car's do, and stay on while he waves, where nothing of them is seen. **A:** they go out as he lifts his foot for the first step off, over three frames, and he walks off with two dark red lenses on his back. **B:** they stay lit until he is gone. The clips are written as A, and A is what the app's pictures are packed from; `turn_away` and `walk_off` in `animate_milo.py` can be told to leave them lit, which is how `render_wave2.py` still draws B, for the second film.

**The clips** are in the table above: `WalkIn`, `WalkStop`, `TurnAway`, `WalkOff`, with `Wave` between the second and the third. What they are built on:

- **In these four the bone `root` says which way he faces, and 0 is facing the viewer.** `WalkIn` is turned 58 degrees to his left (`WALK_IN_TURN`), `WalkOff` 135 (`WALK_OFF_TURN`). So they are drawn from the studio's camera as they are, the rig not turned, and `Wave` goes between them unchanged. (The moonwalk and the run are written facing the way he was built, and the script that draws them turns the rig.) `render_clips.py` draws the four like any clip, and shows them as the greeting does.
- **`Wave` does not key the brake lights.** Whoever shows the five clips one after the other keeps the lights lit through it: `render_wave2.py` does, and so they are lit in the app's picture. From the front that changes nothing: three frames of the wave drawn lit and unlit differ by 1 of 255 in a pixel at most.
- **Both walks go the same way through the room,** 96.5 degrees to the left of facing the viewer (`WALK_HEADING`), which is level in the picture as the studio's camera sees it. Walking in, that is 38.5 degrees to his left of straight ahead; walking off, 38.5 to his right: the two views are mirror images of each other about the way he goes. As in the moonwalk and the run, the angles are right for each other and for this camera, and for no other.
- **A longer stride than `Walk`'s.** `Walk` goes 0.60 of a unit in a cycle, and at the greeting's pace, twelve frames to a cycle, that is half a phone's screen in four seconds: in and out again would have taken ten. This walk goes 1.04 units in a cycle (`PACE_STRIDE`), a planted foot going 0.52 back under him: 71.5 dp in the picture, 5.96 dp a frame, 143 dp a second. (The moonwalk glides 137.) Otherwise it is `Walk`'s walk: a foot lands on its heel, is flat, and leaves from its toe, and each arm swings with the opposite leg.
- **A shoe rolls over its two edges, and each stays where it is** (`stepped`). A shoe is tipped about the point under its ankle, so the heel's edge, on which it lands, and the toe's, from which it leaves, would creep by what the tipping comes to. The ankle is carried by that much instead.
- **His hips are half as if square to the way he goes, his shoes point most of the way there, and the farther foot goes a little further** (`PACE_SQUARE`, `PACE_TOES`, `PACE_FAR`): the run's three, and for the run's reasons. One more: his shoes point a little towards the viewer as he walks in and a little away as he walks off, and what is nearer is drawn larger. So walking off both feet go a third of a hundredth further, and walking in as much less (`PACE_NEAR`). With that a frame carries all four feet equally far back in the picture: 15.63 to 15.65 pixels, drawn with 2.625 to the dp.
- **How well a planted foot keeps its place.** `render_wave2.py` asks where the camera draws the heel's edge and the toe's edge of each shoe in every frame in which that edge is on the floor. Moved a twelfth of the stride with every frame, an edge stays within 1.06 pixels of its place from left to right walking in, and 0.92 walking off, drawn with 2.625 pixels to the dp: 0.40 and 0.35 dp; and within a hundredth of a pixel up and down. From one frame to the next an edge moves 0.26 pixels at most, 0.10 dp. What is left is the view: a shoe that does not lie square to the camera has its toe nearer than its heel, and what is nearer goes by faster. A shoe that pointed all the way to where he is going would lie squarer, and was tried: its toe is then out where his hand swings, and went into it.
- **He stops and starts in the middle of a step** (`PACE_FIRST`): the frame in which his left foot is flat under him and his right is in the air beside it, at the top of its way forward. To stop, that foot is put down; to start, it is lifted, straight up; the other one never moves. That frame is both walks' first, so the walk in ends on the frame before it and the walk off begins with it. He is at full pace until the frame he stops in and from the first frame he goes: the top of him rocks on the way he was going as he stops, and before he goes he sinks a little and lifts his foot over four frames.
- **He turns in the air.** Both turns are hops: he crouches, the top of him already turning, leaves the floor, turns, and lands (`HALT_HOP`, `AWAY_HOP`: four frames in the air for the 58 degrees to the viewer, six for the 135 away, 29 degrees a frame at most). His feet turn with him only while they are off the floor, so nothing grinds round on the ground: through the whole middle part a shoe's edge that is on the floor is drawn where it was the frame before, to the hundredth of a pixel. The wave the app has turns with a hop too, a lower one.
- **The turn away goes through a side view,** in which his body, a slab, is edge on to the viewer for one frame, in the air. The frame after it the lenses and their halo are in view.
- **The brake lights are in view and lit for ten frames in A,** from the second frame before he lands until he begins to lift his foot, 0.42 seconds, and for two more as they go out. He stands for seven frames after he lands (`STEP_OFF`), which is for them to be seen.
- **Nothing of him goes into anything else of him** in the two walks and the two turns (`check_wave2.py`, which asks what `check_run_slide.py` asks). It took holding his hands further out as they swing back: his feet go to one side as they come forward, turned out, and a hand that swung back beside him was in the shoe coming up under it. **In `Wave` itself the waving hand touches the big dial's housing in twelve frames and his body in three,** by the same check. That is the clip the app has shown since 0.3.1, and it was left as it is.
- **The wave's frames are the wave the app has,** drawn by the same function from the same camera under the same lights. They are not the same pixels: in this larger picture he lies a part of a pixel off where he lies in the app's, so the two differ along every edge, and in flat colour by 1.5 of 255 on average.
- **The needles** show a brisk 46 km/h as he walks, fall as he stops, are the wave's while he waves, jump for the turn away and are the walk's again as he goes.

**The previews** are drawn and laid over a picture of Home (`marketing/screenshots/home.png`) like this:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_wave2.py -- /tmp/milo-wave2 2.625
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_wave2.py -- /tmp/milo-wave2 2.625 --plain
python3 design/mascot/scripts/preview_wave2.py /tmp/milo-wave2 /tmp/milo-wave2-preview
```

- **The first step draws the frames** (five minutes), 680 by 576 pixels each, on a see-through background with the halo: one cycle of each walk; the middle part, 80 frames (`WalkStop` 11, `Wave` 49, `TurnAway` 20); and, for B, the walk off again with the lights lit and the three frames of the middle in which A's lights go out. 2.625 is how many pixels the picture of Home has to the dp. It prints, and writes into `wave2.json`, what the other scripts need: the picture's size, where he stands in it, how far a cycle carries him, and where each shoe's heel and toe stand on the floor in every frame. It also prints how well a planted foot keeps its place. `--measure` prints and draws nothing; `--every 3` draws every third frame of the middle, for a quick look.
- **It takes the fill light's shine off for two or three frames of each turn** (`sheen` in `render_wave2.py`; nothing is saved). His front is flat and glossy, and the studio's fill light is a large soft one to the viewer's right: turned about 27 degrees towards it, the whole of his front mirrors it, and for two frames the green went pale and the M grey, like a flicker. Walking, standing and waving he is drawn under the lights as they are.
- **The second step draws the walk off with the lights lit and no halo** (`--plain`), for a still on white: the halo is for a dark ground only.
- **The third writes,** for A and for B: `wave2-preview-A.mp4` (the whole screen, 1080 by 2400 pixels, 24 frames a second, no sound), `wave2-preview-A.gif` (the foot of the screen, smaller), `wave2-sheet-A.png` (a frame from every quarter second, numbered) and `wave2-sheet-middle-A.png` (every frame from four before he stops to eight after he goes); and four stills of him walking off, lit and unlit, on the dark page and on white. It plays the greeting as the app plays it: he is 150 dp high, his soles are 4 dp above the bottom bar's tile, the app's soft shadow lies under his feet, and every frame of a walk stands a twelfth of the stride to the right of the one before. He comes in from just beyond the left edge, does the middle part in the middle of the screen, and walks on until he is wholly beyond the right edge. **On a phone 411 dp wide A is 173 frames, 7.21 seconds (48 in, 80 in the middle, 45 out), and B 175 frames, 7.29 seconds:** B's halo reaches further behind him and takes two frames longer to leave. The moonwalk is 176 frames there.
- **It lays him to the part of a pixel,** as the app does. Laid on whole pixels, as the moonwalk's preview lays him, a planted foot shivers by half a pixel from frame to frame.
- **It measures the seams.** The middle's first frame is the walk in's first, drawn again: over the dark page the two differ by 1 of 255 in a pixel at most. From the walk in's last frame to the middle's first, an edge of a shoe that is on the floor moves 0.07 pixels across at most; from the middle's last frame through the walk off's first two, 0.23. And the step from the middle's last frame to the walk off's first changes the picture less than the two steps before it (2.5 of 255 a pixel on average, against 7.7 and 5.8; with the lights left lit, 2.5 against 5.0 and 5.0).
- **Nothing of the previews is committed** but the six pictures in `renders/`, which are packed from the frames and the films like this (1.65 MB together):

```
python3 design/mascot/scripts/pack_clips.py wave2 /tmp/milo-wave2 /tmp/milo-wave2-preview
```

| Picture | What it shows | Size |
|---|---|---|
| `milo_walk_in.webp` | One cycle of `WalkIn`, he alone, drawn on the spot: 12 frames of 488 by 520 pixels, he 394 high. See-through; starts again for ever | 120 KB |
| `milo_walk_off.webp` | One cycle of `WalkOff`, the same, the brake lights unlit | 96 KB |
| `milo_walk_off_lit.png` | Frame 8 of the walk off with the brake lights lit, he alone. See-through, 488 by 520 pixels, and no halo: right on any ground | 148 KB |
| `milo_walk_off_lit_on_dark.png` | The same frame as film B shows it: on the app's dark page, with the halo and his shadow, 680 by 576 pixels | 108 KB |
| `milo_wave2_a_on_home.webp` | Film A at half its size, the foot of the screen, Home behind him: 177 frames of 540 by 392 pixels, 7.4 seconds. Starts again for ever; no sound | 585 KB |
| `milo_wave2_b_on_home.webp` | Film B, the same: 179 frames, 7.5 seconds | 594 KB |

**The app's two pictures** are made from the same frames, drawn with 2.5 pixels to the dp, which is what `render_wave2.py` draws when it is told nothing else:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_wave2.py -- /tmp/milo-wave2-app
python3 design/mascot/scripts/pack_wave2.py /tmp/milo-wave2-app
```

| Picture | What it shows | Size |
|---|---|---|
| `app/src/main/res/drawable-nodpi/mascot_walk_in_and_off.webp` | One cycle of the walk in, 12 frames, and then one cycle of the walk off, 12 frames. A still picture of 2,160 by 2,640 pixels: the frames lie side by side, four across and six down. The app draws the frame it wants, because it moves the mascot with every frame: the first twelve on the way in, the other twelve on the way off | 184 KB |
| `app/src/main/res/drawable-nodpi/mascot_stop_and_wave.webp` | The middle part, from the walk in's first frame: `WalkStop`, `Wave` and `TurnAway`, 80 frames. An animated picture, which Android plays once | 942 KB |

- **The first step draws the frames** (two and a half minutes), 648 by 552 pixels each. The second packs them, in ten seconds.
- **The app's code has to be told what the second step prints:** `MascotPicture` in `core/designsystem/component/MascotWalk.kt` holds those numbers. A frame is 540 by 440 pixels, 216 by 176 dp. His axis is 118.4 dp from its left edge. Walking in, his soles are 5.0 dp above its lower edge (walking off, the lowest of him is 1.2 dp higher, which is the view, not the floor). Walking in he reaches 79.2 dp ahead of his axis, and walking off 61.2 dp behind it. One cycle of either walk, 12 frames, carries him 71.43 dp. The middle part is 80 frames, 3,360 ms. `MascotPicturesTest` holds the two files to the sizes, the frames and the time; `MascotWalkTest` holds the stride against where the first step found four of the shoes' edges on the floor (`wave2.json`, "stood"). The axis, the soles and the reaches are in no test.
- **Both walks are on one sheet,** where each greeting before had one cycle on its sheet: he is seen from another side as he leaves. The app reads one sheet for a greeting and steps through it; a sheet for each walk would weigh the same and need a third picture everywhere. The sheet takes 22.8 MB of the phone's graphics memory while the wave is on the screen, twice what a sheet of one cycle would (FINDINGS_LOG, 2026-10-11).
- **It is packed by `pack_moonwalk.py`'s functions, at its settings:** colour at 50 of 100; the frames cut down to what is drawn in any of them; a frame with a glow in it stored with its see-through channel almost whole (10 of the 80: the turn away, with his lit lights to the viewer); a frame that is the frame before not stored (1 of the 80, so the picture holds 79). No frame is a patch: he bobs with every wave.
- **Colour at 50, where the wave before it had 60.** Laid beside film A at a phone's size, frames packed at 60, 50 and 45 could not be told from the film or from one another; enlarged, 50 is as 60, at 45 the dials' small numbers begin to go soft, and at 40 they smear. The whole greeting weighs 1,195 KB at 60, 1,125 KB at 50, 1,095 KB at 45 and 1,052 KB at 40: colour is not where the weight is. To weigh it again at other settings without touching the app, name a folder and the colours:

```
python3 design/mascot/scripts/pack_wave2.py /tmp/milo-wave2-app --trial /tmp/milo-wave2-trial 60 50 45 40
```

- **The script checks itself:** it reads both packed pictures back and holds every frame against the one that was drawn, over the app's dark page.
- **It packs A.** The folders `middle-lit/` and `out-lit/`, which the first step draws for film B, are not read.
- **The halo makes the middle part right for a dark ground only,** as the moonwalk's is. Over the app's lime tile the glow is an orange mist that darkens the lime: for half a second after the turn away, on a phone on its side or in a window about 280 dp wide (FINDINGS_LOG, 2026-10-11). The frames can be drawn without the halo, as `--plain` draws the walk off for its still.
- **Android says nothing of which frame of an animated picture it shows,** so a phone that falls behind in the middle part begins the walk off from another pose than the one it was drawn to follow. On the emulator all 80 frames were on the screen when they were due.
- **A change to a clip or to the lights** means both steps again, the numbers into `MascotPicture` if any changed, a look at the greeting on a phone or an emulator, and a new film for Shawn if what he approved has changed.

## What to know before writing a clip

- **A hand has a thumb and three fingers:** `finger_a` next to the thumb, `finger_b` the middle and longest, `finger_c` the outer one. A pose curls each finger at its two joints and may spread it sideways (`spread` in `arm_pose`); the thumb folds across the palm with `(112, 0, 50, 62)` or near it. The peace sign is a and b up and apart; the horns are a and c up.
- **The hands are large and the arms short.** A fist beside the head is clear of it only with the forearm leaning a little outwards and the fingers to the viewer; curled in towards the head, as a real arm flexes, it is inside the head. A hand held up beside the head has about a finger's width to spare.
- **No overshoot towards the head.** `ThumbsUp` swings a little past its pose and back. A hand beside the head that does so goes through a dial: `Peace` and `RockOn` swing outwards as they arrive instead.
- **An arm goes from one pose to another by the shortest way,** which between "forearm up" and "forearm down" can be through the body. `curved()` draws the way towards a third pose: `Flex` takes its fists round the outside with it.
- **Turned about 10 degrees to its right and leaning back, the dials go pale.** Their glossy black faces then mirror the studio's key light into the camera, the same shine the greeting found in its turn. The pictures are drawn with the faces dulled, but the model and its `.glb` keep the gloss, so `Flex` does not lean back in the pose in which it is turned that way.
- **His two hands only meet in front of his middle.** Each arm reaches about three quarters of a unit from its shoulder, and the shoulders are a unit apart. A hand held up beside the head cannot be reached by the other (see "The party").
- **A shoe cannot be lifted far.** The legs are shorter than a shoe is high: a foot under him can rise about a fifth of a unit before its shoe is in his body, and a shoe tipped far onto its toe has its heel there sooner. A body that leans brings its low corner down as well, over whatever shoe is under it (see "The run and the slide").
- **What goes into what does not always show in the frames.** A shoe's top inside the foot of his body is hidden by the body, and a fist's fingers in his side look like a fist beside him. The first drawing of the run had both in half its frames, and a fist in the small dial; they were found by asking the posed meshes whether they cross (`check_run_slide.py`), not by looking.
- **A leg stretched to more than twice its length shows its end.** The hose's cut end is inside the shoe's collar while the leg is short; stretched far, it sticks out past it like a pipe. The bolt's first drawing had it.
- **A foot that is on the floor must not turn with him.** The bone `root` turns his feet too. A clip that turns him does it while his feet are in the air (the hops of `WalkStop` and `TurnAway`), or lets them skid, as the moonwalk's stop and the slide do.
- **His flat front mirrors the studio's fill light when he is turned about 27 degrees to his left of facing the viewer:** the whole of it goes pale, and the M grey, for as long as he is at that angle. A clip that turns through it shows it for a frame or two; `render_wave2.py` takes it out as it draws (see "The wave, walked through"), and no other script does.
- **Look at the frames.** `render_clips.py` at 250 pixels and a sheet of every second frame shows a hand in a dial, or a pop, that the numbers do not.
