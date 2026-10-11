# The mascot

MilO's mascot: a green block with a speedometer and a rev counter for eyes, the app's "M" for a mouth, hose arms and legs, white gloves and green shoes. Built in Blender 5.2 on 2026-10-09 from the picture in `reference.webp`. It is a 3D model here. The app carries no model: it shows two pictures of it, drawn here once (`docs/adr/ADR-007-mascot-animated-picture.md`). The website shows three more, drawn the same way.

Nothing in this folder is part of the app's build. The two pictures the app carries are written from here into `app/src/main/res/drawable-nodpi/`, the website's three into `website/mascot/`, and all five are committed.

| File | What it is |
|---|---|
| `milo_mascot.blend` | The Blender file: model, skeleton, clips, and a small studio (camera, lights, white backdrop) |
| `scripts/build_milo.py` | Builds the model and its skeleton from nothing |
| `scripts/studio_milo.py` | Builds the camera, the lights and the backdrop, and stands the reference picture beside the model |
| `scripts/animate_milo.py` | Writes the clips |
| `scripts/export_milo.py` | Writes the `.glb` file and prints what is in it |
| `scripts/render_greeting.py` | Draws the frames of the app's greeting: the walk, and the turn and wave |
| `scripts/pack_greeting.py` | Packs those frames into the app's two pictures |
| `scripts/render_clips.py` | Draws the frames of any clip, at any size |
| `scripts/render_dials.py` | Draws the two dials without their needles, and the needles' blades alone, for the website |
| `scripts/render_lights.py` | Draws the brake lights to be shown: his back with them off and lit, and a turn-around |
| `scripts/pack_clips.py` | Packs those frames into the pictures in `renders/`, and into the website's three |
| `scripts/render_moonwalk.py` | Draws the frames of the moonwalk, for a preview of it as a greeting (below) |
| `scripts/preview_moonwalk.py` | Lays those frames over a picture of Home and writes a film of it. Plain Python, not run in Blender |
| `export/milo_mascot.glb` | The model with every clip, without the hat (2.67 MB), for whatever comes later. Nothing uses it today |
| `renders/milo_thumbs_up.png` | The mascot as Blender draws it, in the pose of the reference picture |
| `renders/milo_flex.webp`, `milo_peace.webp`, `milo_rock_on.webp` | Three clips as animated pictures with a see-through background, for whatever comes later (below) |
| `renders/milo_flex.png`, `milo_peace.png`, `milo_rock_on.png` | A still of the pose each of them holds |
| `renders/milo_back.png`, `milo_back_lit.png`, `milo_back_three_quarter_lit.png` | His back with the brake lights off and lit, and from behind and to one side with them lit, on a see-through background (below) |
| `renders/milo_back_on_dark.png`, `milo_back_lit_on_dark.png`, `milo_back_three_quarter_lit_on_dark.png` | The same three on the app's dark page, with the halo round the lit lights |
| `renders/milo_turn_around_on_dark.webp` | A turn-around on the dark page, the lights coming on while his back is to the viewer: an animated picture that starts again for ever |
| `reference.webp` | The picture the mascot was built from |

**The scripts are the source, the `.blend` is their result.** To change the mascot, change a script and run the first four again, in the order above, rather than editing the Blender file by hand: what is done by hand is gone at the next run. Each of them only rebuilds its own collections (`MilO_Mascot`, `MilO_Studio`, `MilO_Reference`). After a change to the model, or to a clip that a picture is drawn from, that picture has to be drawn again too (below).

Without Blender's window, which is how it was last run (2026-10-09). `-b` is "no window", and the last part saves the file, which nothing else does:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/build_milo.py --python design/mascot/scripts/studio_milo.py --python design/mascot/scripts/animate_milo.py --python design/mascot/scripts/export_milo.py --python-expr "import bpy; bpy.ops.wm.save_mainfile(compress=True)"
```

It takes a few seconds. Blender keeps the file it replaced beside the new one as `milo_mascot.blend1`; git ignores it, and it can be deleted. On this Mac `blender` is `/Applications/Blender.app/Contents/MacOS/Blender`.

The same command without `-b` and without its last part opens Blender's window and leaves the file to be saved by hand. The scripts that draw pictures are run differently and are not part of it.

**Run twice, it writes the same clips and the same `.glb`, byte for byte.** That is the check that a change to a script left the other clips alone: build once from the scripts as they were and once from the changed ones, and compare. The Blender file that was committed before the evening of 2026-10-09 was not quite what its scripts write: about 850 of its 29,600 key values were off by less than a millionth, the mark of a file built in several sittings. Nothing that can be seen.

## The clips

Each is one action, at 24 frames a second, starting on frame 0. Every clip but the loops, `WaveUp` and the moonwalk's four begins and ends in the same standing pose, so one can follow another. The needles move in every clip.

`Flex`, `Peace` and `RockOn` were added on 2026-10-09 for pictures to come ("future content ideas or use on the website"); nothing plays them yet. `WaveUp` and `WaveLoop` were added on 2026-10-10 for the website, where he waves for as long as a pointer is on him. The four clips of the moonwalk were added the same day for a greeting Shawn asked for; **they are a preview, and nothing plays them yet** (see "The hat and the moonwalk").

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
| `Gauge_Speed` | 2 s | The speed needle alone, from 0 to 240 |
| `Gauge_RPM` | 2 s | The RPM needle alone, from 0 to 8 |

The two gauge clips move one bone each and nothing else, so they can be laid over any other clip and set to a live value: speed ÷ 240 of the way through the first, RPM ÷ 8 of the way through the second.

## The app's pictures

The app's greeting is two pictures (WebP with a see-through background; a frame is 600 by 420 pixels, 24 frames a second):

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

To give the app another clip, draw it as one more picture the same way and add it to `MascotPictures` in `core/designsystem/component/Mascot.kt`: an animated picture if the mascot stands where it is, a sheet of frames if the app has to move it in step. Each second of a clip is about a third of a megabyte.

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
- **The brake lights are driven by a third slider, `brake`,** from 0, unlit, to 1, full on. A clip keys the slider, never the lenses' material. Only the moonwalk's clips key it (see "The brake lights").
- **The hat is put on by a fourth slider, `hat`:** 0 is no hat, 1 is the hat. Only the moonwalk's clips key it (see "The hat and the moonwalk").
- **The bone `root` turns the whole mascot, feet and all,** about the point he stands on. Only the moonwalk's clips key it, for the skid and the turn. Every other clip leaves him facing the way he was built.
- **Arms and legs are hoses.** Their four bones on each side are bendy bones that carry no mesh; rows of short `hose.*` bones follow them and carry it. A `.glb` file cannot hold a bendy bone, and this is what keeps the hoses round in the export.
- **`Walk` walks on the spot,** with a short stride: each foot goes 0.30 of a unit back while it is planted, on a mascot 2 units high. The greeting moves the picture by that much, so a longer or shorter stride here changes how long the walk across the screen takes.
- **The feet lead the legs.** Move a foot and its leg follows; lower the body and the knees bend.
- The "M" is the app's own mark: its outline is read from `app/src/main/res/drawable/ic_launcher_foreground.xml`, and the numbers on the dials are set in the app's `sora.ttf`.

## The brake lights

Two round red domes low on his back, close together and proud of the body, where a truck has its tail lights and he would have his behind (Shawn, 2026-10-10: "add brake light to his back side like a butt. I would like these to be able to light up"). He chose them from three designs drawn for him ("use A for the brake lights"); the other two are not in the script (FINDINGS_LOG, 2026-10-10). Nothing shows from the front, and nothing in the app or on the website shows them yet.

- **The lenses are one object, `Brake_Lights`, and their red is one material of its own, `MilO_Brake_Lens`:** a dark, glossy red while it is unlit, in thin rims of the black rubber.
- **They cast no shadow,** like the glass over the dials. The studio's rim light stands behind him; with a shadow, the lights darkened the top of a shoe by a few hundred pixels in every picture taken from the front.
- **None of the pictures there were changes.** Rays cast from the camera to every point of the lenses, in every frame of every clip as it is drawn, reach none of them; and the greeting's and the website's frames, drawn again, are the committed ones to within what two runs of the renderer differ by.
- **Lit, the lens glows:** the material's emission strength follows the slider `brake`, from 0 to 4 (`BRAKE_GLOW`), in a red kept so pure that a lit lens is drawn red and not pink (`lens_material`). The light also falls on the body round the lens.
- **A clip that lights them keys the slider itself.** In `animate_milo.py` it calls `set_brake(how far)` at every frame and `key_brake(frame)` after `key_pose(frame)`. The slider is not part of `key_pose`: that would add a curve to every clip there is. The moonwalk's clips do it (`key_clip` keys the pose and, for them, the lights, the hat and the turn), and `render_lights.py` does it in clips of its own, in memory. When a clip is drawn, the lights are put out and the hat is taken off first (`reset_pose`), so one that does not key them is not drawn with what the last one left behind.

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

**A preview. The app does not play it.** Shawn, 2026-10-10: "i want to have a animation with him moon walking on to the screen stops mid way. adoes some hip thrusts than spins and continues moon walking. [...] if we can add a hat for this animation it would be cool. i want it to be like Micheal Jackson video", to "take turns with the wave", and "show me a preview first". What is here is the hat, the four clips and the two scripts that make the preview; nothing under `app/` was changed for it.

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

**What the app would need, if this is built in:** the glide as a sheet of 14 stills, as the walk is now, played twice, in and out; the middle as an animated picture of 80 frames; and both higher than the greeting's 240 by 168 dp, because of the hat and the halo's room (the preview's frames are 226 by 235 dp). Packed as the greeting's pictures are, with 2.5 pixels to the dp, they came to 179 KB and 987 KB in a trial: the app's pictures of the mascot would go from 0.9 MB to 2.0 MB, and `MascotPicturesTest` holds them under one today. The halo makes the pictures right for the dark page only. The numbers `MascotPicture` holds would be these scripts' to print. And a rule for when he moonwalks and when he waves.

## What to know before writing a clip

- **A hand has a thumb and three fingers:** `finger_a` next to the thumb, `finger_b` the middle and longest, `finger_c` the outer one. A pose curls each finger at its two joints and may spread it sideways (`spread` in `arm_pose`); the thumb folds across the palm with `(112, 0, 50, 62)` or near it. The peace sign is a and b up and apart; the horns are a and c up.
- **The hands are large and the arms short.** A fist beside the head is clear of it only with the forearm leaning a little outwards and the fingers to the viewer; curled in towards the head, as a real arm flexes, it is inside the head. A hand held up beside the head has about a finger's width to spare.
- **No overshoot towards the head.** `ThumbsUp` swings a little past its pose and back. A hand beside the head that does so goes through a dial: `Peace` and `RockOn` swing outwards as they arrive instead.
- **An arm goes from one pose to another by the shortest way,** which between "forearm up" and "forearm down" can be through the body. `curved()` draws the way towards a third pose: `Flex` takes its fists round the outside with it.
- **Turned about 10 degrees to its right and leaning back, the dials go pale.** Their glossy black faces then mirror the studio's key light into the camera, the same shine the greeting found in its turn. The pictures are drawn with the faces dulled, but the model and its `.glb` keep the gloss, so `Flex` does not lean back in the pose in which it is turned that way.
- **Look at the frames.** `render_clips.py` at 250 pixels and a sheet of every second frame shows a hand in a dial, or a pop, that the numbers do not.
