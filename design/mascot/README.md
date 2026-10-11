# The mascot

MilO's mascot: a green block with a speedometer and a rev counter for eyes, the app's "M" for a mouth, hose arms and legs, white gloves and green shoes. Built in Blender 5.2 on 2026-10-09 from the picture in `reference.webp`. It is a 3D model here. The app carries no model: it shows six pictures of it, two for each of its greetings and two for its one-year party, drawn here once (`docs/adr/ADR-007-mascot-animated-picture.md`). The website shows three more, drawn the same way.

Nothing in this folder is part of the app's build. The six pictures the app carries are written from here into `app/src/main/res/drawable-nodpi/`, the website's three into `website/mascot/`, and all nine are committed.

| File | What it is |
|---|---|
| `milo_mascot.blend` | The Blender file: model, skeleton, clips, and a small studio (camera, lights, white backdrop) |
| `scripts/build_milo.py` | Builds the model and its skeleton from nothing |
| `scripts/studio_milo.py` | Builds the camera, the lights and the backdrop, and stands the reference picture beside the model |
| `scripts/animate_milo.py` | Writes the clips |
| `scripts/export_milo.py` | Writes the `.glb` file and prints what is in it |
| `scripts/render_greeting.py` | Draws the frames of the app's first greeting, the wave: the walk, and the turn and wave |
| `scripts/pack_greeting.py` | Packs those frames into the wave's two pictures |
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
| `scripts/render_run_slide.py` | Draws the frames of the run and the slide: with their smoke, without it, or the smoke alone, for a preview of them (below) |
| `scripts/preview_run_slide.py` | Makes a film of those frames on a phone's screen, moving him across it, with sound if it is given any. Plain Python, not run in Blender |
| `scripts/check_run_slide.py` | Says in which frames of the run and the slide a shoe or a hand goes into his body, a dial, a shoe or the other hand |
| `export/milo_mascot.glb` | The model with every clip, without the hat, the party cap, the popper, the confetti and the smoke (3.25 MB), for whatever comes later. Nothing uses it today |
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

Each is one action, at 24 frames a second, starting on frame 0. Every clip but the loops, `WaveUp`, the moonwalk's four, `OneYear` and the five of the run begins and ends in the same standing pose, so one can follow another. `OneYear` begins in it, in his party cap, and ends with both arms up. The five of the run follow one another and he never stands in them. The needles move in every clip.

`Flex`, `Peace` and `RockOn` were added on 2026-10-09 for pictures to come ("future content ideas or use on the website"); nothing plays them yet. `WaveUp` and `WaveLoop` were added on 2026-10-10 for the website, where he waves for as long as a pointer is on him. The four clips of the moonwalk were added the same day for a second greeting Shawn asked for; the app plays them since then, in turn with the wave (see "The hat and the moonwalk"). `OneYear` was added the same day for the day the app has been used for a year, with the confetti's own clip beside it; the app plays it since then, on that day and on demand (see "The party"). `Run`, `SpinUp`, `Burnout`, `BrakeSlide` and `Slide` were added the same day for an animation Shawn asked for without a place for it yet, with the smoke's own clip beside them; **they are a preview, and nothing plays them yet** (see "The run and the slide").

| Clip | Length | What happens |
|---|---|---|
| `Idle` | 4 s, loops | Stands, breathes, sways a little |
| `Wave` | 2 s | The right arm goes up, the open hand waves |
| `ThumbsUp` | 2 s | The pose of the reference picture, and back |
| `Walk` | 1 s, loops | Walks on the spot. The greeting plays it at twice this speed |
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
| `RunSlide_Smoke` | 5.25 s | Not his: the smoke's own clip, on the smoke's own skeleton (`MilO_Smoke_Rig`), for those five shown one after the other: a little dust at every footfall, puffs kicked back from the shoe that scuffs while he runs on the spot, a burst where he pushes off, and a trail from both shoes from the brake on |
| `Gauge_Speed` | 2 s | The speed needle alone, from 0 to 240 |
| `Gauge_RPM` | 2 s | The RPM needle alone, from 0 to 8 |

The two gauge clips move one bone each and nothing else, so they can be laid over any other clip and set to a live value: speed ÷ 240 of the way through the first, RPM ÷ 8 of the way through the second.

## The app's pictures

The app has two greetings, which take turns, and each is two pictures: a still sheet of frames the app steps through while it moves the mascot, and an animated picture Android plays while he stays where he is. All are WebP with a see-through background, 24 frames a second, 2.5 pixels to the dp. The moonwalk's two are in "The hat and the moonwalk", below; the wave's are these. (The one-year party's two are of another kind, both animated and with fewer pixels to the dp: "The party", below.) A frame of theirs is 600 by 420 pixels:

| Picture | What it shows | Size |
|---|---|---|
| `app/src/main/res/drawable-nodpi/mascot_walk.webp` | One cycle of the walk toward the left, 12 frames. A still picture: the frames lie side by side, four across and three down. The app draws the frame it wants, because it moves the mascot with every step | 98 KB |
| `app/src/main/res/drawable-nodpi/mascot_turn_and_wave.webp` | From the walk's first frame: the turn to the viewer in 11 frames, then the Wave clip. An animated picture of 60 frames, which Android plays once | 782 KB |

They are made in two steps. The first needs the Mac's Blender. The second needs `cwebp` and `webpmux` (`brew install webp`) and `ffmpeg` (`brew install ffmpeg`). The two folders are for the frames and can be anywhere outside the repository:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_greeting.py -- /tmp/milo-greeting/walk /tmp/milo-greeting/wave
python3 design/mascot/scripts/pack_greeting.py /tmp/milo-greeting/walk /tmp/milo-greeting/wave
```

- **The first step draws the frames** (a minute and a half), from the studio's camera and under its lights, with the backdrop hidden so that the background is see-through. It saves nothing in the Blender file. At its end it prints the picture's size, how far the mascot reaches ahead while it walks, where its soles are, and its stride.
- **The second step packs them** into the two pictures and prints what each weighs.
- **The app's code has to be told what the first step printed:** `MascotPicture` in `core/designsystem/component/MascotWalk.kt` holds those numbers. `MascotPicturesTest` fails if the pictures' size, the sheet's rows, the animated picture's frames or how often it plays are not what stands there. The stride, the reach and the soles are not in the files, and no test can check them: a wrong stride shows as feet that slide.
- **What can be changed at the top of `render_greeting.py`:** how high the mascot is drawn, how far it is turned while it walks, how fast it steps, and how long the turn takes.
- **The dials' black faces are drawn without their shine** (`dull_the_dials` in `render_greeting.py`; nothing is saved). They are glossy in the model, and as the mascot came round to the viewer the big dial mirrored the key light: pale grey for three frames, like a flicker.

Seen from the studio's camera, which looks down on the ground a little, the foot nearer the viewer stands about 11 dp lower in the picture than the other, and a planted foot rises about 3 dp while it steps back. That is the view: the mascot walks a little toward the viewer. From left to right a planted foot stays where it is.

To give the app another greeting, draw it as two more pictures the same way, an animated picture for the part in which the mascot stands where it is and a sheet of frames for the part in which the app has to move it in step, and add it as the moonwalk was added: a value of `MascotGreetingKind`, its numbers beside `MoonwalkPicture`, and its files in `Mascot.kt` (`core/designsystem/component/`). A frame of the mascot at this size is about 10 KB however it is packed, so each second of a clip is about a quarter of a megabyte, and `MascotPicturesTest` holds each greeting under one megabyte.

**Two runs do not give the same bytes.** Drawn twice from the same file on the same Mac, every frame differs from its twin in a few dozen pixels, by 3 of 255 at most, and the packed pictures differ by a few bytes in 800,000. So the app's pictures cannot be checked by drawing them again and comparing the files: compare the frames, and expect that much. A change that is not meant to touch them leaves the committed pictures alone.

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
- **The brake lights are driven by a third slider, `brake`,** from 0, unlit, to 1, full on. A clip keys the slider, never the lenses' material. Only the moonwalk's clips and the run's key it (see "The brake lights").
- **The hat is put on by a fourth slider, `hat`:** 0 is no hat, 1 is the hat. Only the moonwalk's clips key it (see "The hat and the moonwalk").
- **Three more sliders are for the party:** `party_hat` puts the party cap on, `popper` puts the party popper in his left fist, and `pull` puts the bead on the popper's string into his right hand's fingers. All three are 0 where the file is saved. Only `OneYear` keys them (see "The party").
- **The bone `root` turns the whole mascot, feet and all,** about the point he stands on. Only the moonwalk's clips key it, for the skid and the turn, and the run's, for the slide. Every other clip leaves him facing the way he was built.
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
- **A clip that lights them keys the slider itself.** In `animate_milo.py` it calls `set_brake(how far)` at every frame and `key_brake(frame)` after `key_pose(frame)`. The slider is not part of `key_pose`: that would add a curve to every clip there is. The moonwalk's clips do it (`key_clip` keys the pose and, for them, the lights, the hat and the turn), the run's do it (the lights and the turn, `key_run`), and `render_lights.py` does it in clips of its own, in memory. When a clip is drawn, the lights are put out and the hat is taken off first (`reset_pose`), so one that does not key them is not drawn with what the last one left behind.

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
- **`pack_moonwalk.py` does four things `pack_greeting.py` does not,** and says why at its top:
  - **It cuts the frames down** to what is drawn in any of them and 8 pixels more, made up to 480 by 480 pixels (192 dp). The room the first step leaves for the halo is mostly empty. It cost no bytes, but it cost the phone memory: the sheet is 12.9 MB there instead of 18.6.
  - **A frame with a glow in it keeps its see-through channel almost whole** (20 of the 80: the skid and the turn). Stored as the wave's pictures store it, in twelve steps, an edge is fine and a soft halo is drawn as rings.
  - **A frame that is the frame before is not stored** (2 of the 80): the frame before is shown twice as long.
  - **A frame in which only the needles move is stored as the small box that changed** (5 of the 80), laid over the frame before. So the picture holds 78 stored frames, and a frame may depend on the one before it.
- **Colour is packed at 50 of 100,** where the wave's is at 60: he is never still in these pictures, and at a phone's size a frame at 50 could not be told from one at 60. At 40 the numbers on the dials and the lines between his fingers smear.
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

**A preview. The app does not play it.** Shawn, 2026-10-10: "i was thinking one where the mascot runs on to the screen. than he runs in one spot in the center for 2 seconds. than sort of turns and slides out. if we can make smoke from his shoes or something visual that would be cool. the lights on his butt should be off tell he stops and slides and than turn on like a car stopping. im not sure where to use this animation yet"; about the turn, "he doesnt full turn his back towrds us fully"; and "sure a sound effect squeal would be cool". What is here is five clips, the smoke and the two scripts that make the preview; nothing under `app/` was changed for it, and no sound is in the repository.

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
- **Run again with `--reuse` and a name, it lays sound on that film** and writes it beside it under that name. `--squeal` is the tyres' squeal: where it begins in its file is found, not assumed (the first 2 thousandths of a second that are within 20 decibels of its loudest and go on being), and the file is laid so that this lies on the frame the brake lights come on in; it is faded out over its last 0.3 seconds so that it has ended when the last of him has left the screen. `--spin` is a sound for the run on the spot, laid from the frame he gets there and faded out as he brakes. A colon and a number after a file makes it that many decibels louder. Every edge a sound is cut at is faded over a few thousandths of a second. **No sound is kept in this repository.** The ones the preview was made with were generated (ElevenLabs), and their licence is not confirmed.
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

**What the app would need, if this is built in:** somewhere to play it, which is not decided. Then the run as a sheet of 8 stills, as the walk is now, moved 15.5 dp with every frame; and the rest either as he alone, the app throwing the smoke itself, or as the frames with the smoke in them, which are wide because the smoke stays behind. Packed as the greeting's pictures are, with 2.5 pixels to the dp, the run's eight frames came to 91 KB in a trial (228 by 195 dp each); the other 92, from `SpinUp` until he is out, to 1.09 MB as he alone, cut down to him, and to 1.46 MB with the smoke, in a picture of 495 by 242 dp. The app's pictures of the mascot are 0.9 MB together today, and `MascotPicturesTest` holds them under one. The app has to move the picture during `BrakeSlide` and `Slide` as well, by the numbers `run_travel` gives. The halo makes the pictures right for the dark page only. Then a sound whose licence is settled, laid on the frame of the brake, and a rule for when the phone's and the app's sound settings let it be heard.

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
- **Look at the frames.** `render_clips.py` at 250 pixels and a sheet of every second frame shows a hand in a dial, or a pop, that the numbers do not.
